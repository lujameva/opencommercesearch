package org.opencommercesearch.api

/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

import play.api.{Application, Logger, Play}
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader, WithFilters}
import play.api.mvc.Results._
import play.filters.gzip.GzipFilter
import play.libs.Akka
import play.modules.statsd.api.StatsdFilter

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

import org.opencommercesearch.api.job.TaxonomyCheckActor
import org.opencommercesearch.api.models.Availability._
import org.opencommercesearch.api.service.{CategoryService, MongoStorageFactory}
import org.opencommercesearch.api.util.{BigDecimalConverter, CountryConverter}

import org.apache.solr.client.solrj.AsyncSolrServer
import org.apache.solr.client.solrj.impl.AsyncCloudSolrServer

import akka.actor.{Props, Cancellable}
import com.wordnik.swagger.converter.ModelConverters

object Global extends WithFilters(new StatsdFilter(), new GzipFilter(), AccessLog) {
  lazy val RealTimeRequestHandler = getConfig("realtimeRequestHandler", "/get")
  lazy val MaxBrandIndexBatchSize = getConfig("index.brand.batchsize.max", 100)
  lazy val MaxProductIndexBatchSize = getConfig("index.product.batchsize.max", 100)
  lazy val MaxCategoryIndexBatchSize = getConfig("index.category.batchsize.max", 100)
  lazy val MaxRuleIndexBatchSize = getConfig("index.rule.batchsize.max", 100)
  lazy val MaxUpdateFacetBatchSize = getConfig("index.facet.batchsize.max", 100)
  lazy val SearchCustomParams = searchRequestCustomParams

  // @todo deprecate category collections
  lazy val CategoryPreviewCollection = getConfig("preview.collection.category", "categoriesPreview")
  lazy val CategoryPublicCollection = getConfig("public.collection.category", "categoriesPublic")
  lazy val RulePreviewCollection = getConfig("preview.collection.rule", "rulePreview")
  lazy val RulePublicCollection = getConfig("public.collection.rule", "rulePublic")
  lazy val FacetPreviewCollection = getConfig("preview.collection.facet", "facetsPreview")
  lazy val FacetPublicCollection = getConfig("public.collection.facet", "facetsPublic") 
  lazy val SuggestCollection = getConfig("public.collection.suggest", "autocomplete")
  lazy val CategoryCacheTtl = getConfig("category.cache.ttl", 0) //Don't auto expire as it will be flushed by a background job (see taxonomy.stored.checkperiod)
  lazy val MaxPaginationLimit = getConfig("pagination.limit.max", 40)
  lazy val DefaultPaginationLimit = getConfig("pagination.limit.default", 10)
  lazy val MaxFacetPaginationLimit = getConfig("facet.pagination.limit.max", 5000)
  lazy val MinSuggestQuerySize = getConfig("suggester.query.size.min", 2)
  lazy val IndexOemProductsEnabled = getConfig("index.product.oem.enabled", default = false)
  lazy val ProductAvailabilityStatusSummary = availabilityStatusSummaryConfig
  lazy val SpellCheckMinimumMatch = getConfig("spellcheck.minimummatch", "2<-1 3<-2 5<80%")
  lazy val zkHost = getConfig("zkHost", "localhost:2181")

  // @todo evaluate using dependency injection, for the moment lets be pragmatic
  private var _solrServer: AsyncSolrServer = null
  private var _storageFactory: MongoStorageFactory = null
  private var taxonomyCheckJob : Cancellable = null

  def solrServer = {
    if (_solrServer == null) {
      _solrServer = AsyncCloudSolrServer(zkHost)
    }
    _solrServer
  }

  def solrServer_=(server: AsyncSolrServer) = { _solrServer = server }


  def storageFactory =  {
    if (_storageFactory == null) {
      _storageFactory = new MongoStorageFactory
      _storageFactory.setConfig(Play.current.configuration)
      _storageFactory.setClassLoader(Play.current.classloader)
    }
    _storageFactory
  }

  def categoryService = new CategoryService(solrServer, storageFactory)

  def storageFactory_=(storageFactory: MongoStorageFactory) = { _storageFactory = storageFactory }

  override def beforeStart(app: Application): Unit = {
    ModelConverters.addConverter(new BigDecimalConverter(), first = true)
    ModelConverters.addConverter(new CountryConverter(), first = true)
  }

  override def onStart(app: Application) {
    Logger.info("OpenCommerceSearch API has started")

    val taxonomyCheckFrequency = Play.current.configuration.getInt("taxonomy.stored.checkperiod").getOrElse(360000)
    val taxonomyCheckEnabled = Play.current.configuration.getBoolean("taxonomy.stored.enabled").getOrElse(false)

    if(taxonomyCheckEnabled) {
      val actor = Akka.system.actorOf(Props(new TaxonomyCheckActor(categoryService)))
      taxonomyCheckJob = Akka.system.scheduler.schedule(taxonomyCheckFrequency.millis, taxonomyCheckFrequency.millis, actor, "check")
    }
  }

  override def onStop(app: Application) {
    Logger.info("OpenCommerceSearch API shutdown...")
    storageFactory.close
    taxonomyCheckJob.cancel()
  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    Future.successful(ex.getCause match {
   	  case e:IllegalArgumentException => BadRequest(Json.obj(
        "message" -> e.getMessage))
   	  case other =>
        Logger.error("Unexpected error",  other)
        InternalServerError(Json.obj(
          "message" -> "Internal error"))
   	})
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    Future.successful(NotFound(Json.obj(
      "message" -> "Resource not found")))
  }

  override def onBadRequest(request: RequestHeader, error: String) = {
    Future.successful(BadRequest(Json.obj(
      "message" -> error
    )))
  }

  def getConfig(name: String, default: String) = {
    Play.current.configuration.getString(name).getOrElse(default)
  }

  def getConfig(name: String, default: Int) = {
    Play.current.configuration.getInt(name).getOrElse(default)
  }
  
  def getConfig(name: String, default: Boolean) = {
    Play.current.configuration.getBoolean(name).getOrElse(default)
  }

  def searchRequestCustomParams = {
    val customParams = Play.current.configuration.getStringList("search.params.custom").getOrElse(java.util.Arrays.asList())
    customParams.toSeq
  }

  def availabilityStatusSummaryConfig = {
    val summary = Play.current.configuration.getStringList("product.availability.status.summary").getOrElse(
      java.util.Arrays.asList(InStock, Backorderable, Preorderable, OutOfStock, PermanentlyOutOfStock)
    )

    val order = 1 to summary.size
    Map(summary.zip(order):_*) withDefaultValue Int.MaxValue
  }
}


