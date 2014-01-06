package org.opencommercesearch.api.controllers

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

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.api.Logger
import play.api.libs.json.{JsError, JsArray, Json}

import scala.concurrent.Future

import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.Util._
import org.opencommercesearch.api.models.{Rule, RuleList}
import org.apache.solr.client.solrj.request.AsyncUpdateRequest
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.request.AbstractUpdateRequest.ACTION
import org.apache.solr.client.solrj.beans.BindingException
import org.apache.solr.common.SolrDocument

object RuleController extends BaseController {

  def findById(version: Int, id: String, preview: Boolean) = Action.async { implicit request =>
    val query = withRuleCollection(withFields(new SolrQuery(), request.getQueryString("fields")), preview, request.acceptLanguages)

    query.add("q", "id:" + id)
    query.add("fl", "*")

    Logger.debug("Query rule " + id)
    val future = solrServer.query(query).map( response => {
      val results = response.getResults
      if(results.getNumFound > 0 && results.get(0) != null) {
        var doc : SolrDocument = results.get(0)
        Logger.debug("Found rule " + id)
        Ok(Json.obj(
          "rule" -> solrServer.binder.getBean(classOf[Rule], doc)))
      } else {
        Logger.debug("Rule " + id + " not found")
        NotFound(Json.obj(
          "message" -> s"Cannot find rule with id [$id]"
        ))
      }
    })

    withErrorHandling(future, s"Cannot retrieve rule with id [$id]")
  }

  def createOrUpdate(version: Int, id: String, preview: Boolean) = Action.async (parse.json) { request =>
    Json.fromJson[Rule](request.body).map { rule =>
      try {
        val ruleDoc = solrServer.binder.toSolrInputDocument(rule)
        val update = new AsyncUpdateRequest()
        update.add(ruleDoc)
        withRuleCollection(update, preview, request.acceptLanguages)

        val future: Future[SimpleResult] = update.process(solrServer).map( response => {
          Created.withHeaders((LOCATION, absoluteURL(routes.RuleController.findById(id), request)))
        })

        withErrorHandling(future, s"Cannot store Rule with id [$id]")
      }
      catch {
        case e : BindingException =>
          Future.successful(BadRequest(Json.obj(
            "message" -> "Illegal Rule fields",
            "detail" -> e.getMessage)))
      }
    }.recover {
      case e => Future.successful(BadRequest(Json.obj(
        // @TODO figure out how to pull missing field from JsError
        "message" -> "Illegal Rule fields")))
    }.get
  }

  def bulkCreateOrUpdate(version: Int, preview: Boolean) = Action.async (parse.json(maxLength = 1024 * 2000)) { request =>
    Json.fromJson[RuleList](request.body).map { ruleList =>
      val rules = ruleList.rules
      try {
        if (rules.length > MaxUpdateRuleBatchSize) {
          Future.successful(BadRequest(Json.obj(
            "message" -> s"Exceeded number of Rules. Maximum is $MaxUpdateRuleBatchSize")))
        } else {
          val update = withRuleCollection(new AsyncUpdateRequest(), preview, request.acceptLanguages)
          rules map { rule =>
              update.add(solrServer.binder.toSolrInputDocument(rule))
          }

          val future: Future[SimpleResult] = update.process(solrServer).map( response => {
            Created(Json.obj(
              "locations" -> JsArray(
                rules map (b => Json.toJson(routes.RuleController.findById(b.id.get).url))
              )))
          })

          withErrorHandling(future, s"Cannot store Rules with ids [${rules map (_.id.get) mkString ","}]")
        }
      }
      catch {
        case e : BindingException =>
          //Handle bind exceptions
          Future.successful(BadRequest(Json.obj(
            "message" -> s"Illegal Rule fields [${rules map (_.id.get) mkString ","}] ",
            "detail" -> e.getMessage)))
      }
    }.recover {
      case e => Future.successful(BadRequest(Json.obj(
        // @TODO figure out how to pull missing field from JsError
        "message" -> "Missing required fields",
        "detail"  -> JsError.toFlatJson(e))))
    }.get
  }

  /**
   * Post method for the rules endpoint. Will send commit or rollback to Solr accordingly.
   * @param commit true if a commit should be done.
   * @param rollback true if a rollbac should be done.
   */
  def commitOrRollback(preview: Boolean, commit: Boolean, rollback: Boolean) = Action.async { request =>
    if(commit == rollback) {
      Future.successful(BadRequest(Json.obj(
        "message" -> s"commit and boolean can't have the same value.")))
    }
    else {
      val update = withRuleCollection(new AsyncUpdateRequest(), preview, request.acceptLanguages)

      if(commit) {
        update.setAction(ACTION.COMMIT, false, false, false)
        val future: Future[SimpleResult] = update.process(solrServer).map( response => {
          Ok (Json.obj(
            "message" -> "commit success"))
        })

        withErrorHandling(future, s"Cannot commit rules.")
      }
      else {
        update.rollback
        val future: Future[SimpleResult] = update.process(solrServer).map( response => {
          Ok (Json.obj(
            "message" -> "rollback success"))
        })

        withErrorHandling(future, s"Cannot rollback rules.")
      }
    }
  }

  /**
   * Delete method that remove all rules matching a given query.
   * @param query is the query used to delete rules, default is *:*
   */
  def deleteByQuery(preview: Boolean, query: String) = Action.async { request =>
    val update = withRuleCollection(new AsyncUpdateRequest(), preview, request.acceptLanguages)

    update.deleteByQuery(query)

    val future: Future[SimpleResult] = update.process(solrServer).map( response => {
      Ok (Json.obj(
        "message" -> "delete success"))
    })

    withErrorHandling(future, s"Cannot delete rules.")
  }
}
