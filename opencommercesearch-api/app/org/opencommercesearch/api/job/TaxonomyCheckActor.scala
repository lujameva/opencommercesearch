package org.opencommercesearch.api.job

import play.api.Logger
import play.i18n.Lang

import scala.concurrent.ExecutionContext

import org.opencommercesearch.api.service.CategoryService
import org.opencommercesearch.common.Context

import akka.actor.Actor

import ExecutionContext.Implicits.global

class TaxonomyCheckActor(categoryService: CategoryService) extends Actor {
  var lastTaxonomyChange : Option[Long] = None

  def receive = {
    case _ =>
      Logger.info("Check for taxonomy timestamp changes")
      def loadTaxonomy(implicit context: Context) = {
        categoryService.loadTaxonomyTimestamp(lastTaxonomyChange) map { timestamp =>
          timestamp foreach { value =>
            lastTaxonomyChange = timestamp
            Logger.info(s"Loaded taxonomy timestamp changes")
          }
        }}

      val previewContext = Context(preview = true, Lang.forCode("en-US")) //TODO: select proper language here
      loadTaxonomy(previewContext)
      val publicContext = Context(preview = false, Lang.forCode("en-US")) //TODO: select proper language here
      loadTaxonomy(publicContext)
  }
}
