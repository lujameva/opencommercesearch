package org.opencommercesearch.api

import play.api.mvc.{AnyContent, Request}
import java.net.URLDecoder
import org.opencommercesearch.api.Global.{DefaultPaginationLimit, MaxPaginationLimit}
import org.opencommercesearch.api.common.FilterQuery
import org.opencommercesearch.api.Global._
import org.opencommercesearch.api.models.Category
import org.opencommercesearch.common.Context
import org.apache.commons.lang3.StringUtils
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.common.params.GroupParams
import play.api.Play
import scala.collection.JavaConversions._

/**
 * The base query to retrieve product results
 *
 * @param q is the query
 * @param site is the site to search in
 * @param context is the search context
 * @param request is the HTTP request
 *
 * @author rmerizalde
 */
sealed class ProductQuery(q: String, site: String = null)(implicit context: Context, request: Request[AnyContent] = null) extends SolrQuery(q) {
  import Collection._
  import Query._

  private var _filterQueries: Array[FilterQuery] = null
  private val closeoutSites: Set[String] = Play.current.configuration.getString("sites.closeout").getOrElse("").split(",").toSet
  protected val metadataFields = if (request != null) StringUtils.split(request.getQueryString("metadata").getOrElse(""), ',') else Array.empty[String]


  protected def init() : Unit = {
    setFields("id")
    setParam("collection", searchCollection.name(context.lang))

    if (site == null || closeoutSites.contains(site)) {
      addFilterQuery("isRetail:true")
    }

    // product params
    addFilterQuery(s"country:${context.lang.country}")

    // RuleComponent params
    setParam("rule", true)
    setParam("siteId", site)
    // @todo add to API interface
    setParam("catalogId", site)
    withPagination(0, DefaultPaginationLimit)
  }

  init()

  def filterQueries = _filterQueries

  def withPagination() : ProductQuery = {
    var offset = 0
    var limit = DefaultPaginationLimit

    if (request != null) {
      for (o <- request.getQueryString("offset")) {
        offset = o.toInt
      }
      for (l <- request.getQueryString("limit")) {
        limit = l.toInt
      }
    }

    withPagination(offset, limit)
  }

  def withPagination(offset: Int, limit: Int) : ProductQuery = {
    setStart(offset)
    setRows(Math.min(MaxPaginationLimit, limit))
    this
  }

  def facets = metadataFields.isEmpty || metadataFields.contains("facets")

  def withFaceting(field: String = "category", limit: Option[Int] = None) : ProductQuery = {
    setFacet(facets)
    if (facets) {
      addFacetField(field)
      setFacetMinCount(1)

      limit foreach { l =>
        setFacetLimit(l)
      }
    }

    this
  }

  def withSpellCheck(enabled: Boolean = true) = {
    this.setParam("spellcheck", enabled)
    this
  }

  def withFilterQueries() : ProductQuery = {
    _filterQueries = setFilterQueriesFor(this)
    this
  }

  def withSorting() : ProductQuery = {
    val encodedSortParam = if(request != null) request.getQueryString("sort").getOrElse("") else ""
    val sortParam = URLDecoder.decode(encodedSortParam, "UTF-8")
    val sortSpecs = StringUtils.split(sortParam, ",")
    if (sortSpecs != null && sortSpecs.length > 0) {
      val country = context.lang.country
      for (sortSpec <- sortSpecs) {
        val selectedOrder = if (sortSpec.trim.endsWith(" asc")) SolrQuery.ORDER.asc else SolrQuery.ORDER.desc

        if (sortSpec.indexOf("discountPercent") != -1) {
          addSort(s"discountPercent$country", selectedOrder)
        }
        if (sortSpec.indexOf("reviewAverage") != -1) {
          addSort("bayesianReviewAverage", selectedOrder)
        }
        if (sortSpec.indexOf("price") != -1) {
          addSort(s"salePrice$country", selectedOrder)
        }
        if (sortSpec.indexOf("activationDate") != -1) {
          addSort("activationDate", selectedOrder)
        }
        if (sortSpec.indexOf("bestSeller") != -1) {
          addSort(s"field(sellRank$site)", selectedOrder)
        }
      }
    }

    this
  }

  def groupTotalCount = metadataFields.isEmpty || metadataFields.contains("found")

  def withGrouping() : ProductQuery = {
    val collapse = metadataFields.isEmpty || metadataFields.contains("productSummary")
    val limit = if (collapse) 50 else 1
    withGrouping(groupTotalCount, limit, collapse)
  }

  /**
   * Sets parameters to group skus by product
   *
   * @param totalCount if the query should return the total number of groups (products)
   * @param limit the minimum number of skus to group by product
   * @param collapse if the query should output summary fields for prices and discount percentage
   *
   * @return this query
   */
  def withGrouping(totalCount: Boolean, limit: Int, collapse: Boolean) : ProductQuery = {

    if (getRows == null || getRows > 0) {
      set(GroupParams.GROUP, true)
      set(GroupParams.GROUP_FIELD, "productId")
      set(GroupParams.GROUP_TOTAL_COUNT, totalCount)
      set(GroupParams.GROUP_LIMIT, limit)
      set(GroupParams.GROUP_FACET, false)

      setParam("groupcollapse", collapse)
      if (collapse) {
        val country = context.lang.country
        val listPrice = s"listPrice$country"
        val salePrice = s"salePrice$country"
        val discountPercent = s"discountPercent$country"

        setParam("groupcollapse.fl", s"$listPrice,$salePrice,$discountPercent,color,colorFamily")
        setParam("groupcollapse.ff", "isCloseout")
      }

      set("group.sort", s"isCloseout asc, salePrice${context.lang.country} asc, sort asc, score desc")
    }

    this
  }

  def withRules(ruleFilters: Seq[String]) : ProductQuery = {
    setParam("rulePage", true)
    setQuery("*:*")

    for (rules <- ruleFilters) {
      if(rules.nonEmpty) {
        addFilterQuery(rules.substring(rules.indexOf(":") + 1, rules.length()))
      }
    }
    this
  }

  def withBrand(brandId : String) : ProductQuery = {
    addFilterQuery(s"brandId:$brandId")
    setParam("brandId", brandId)
    this
  }

  def withAncestorCategory(categoryId : String) : ProductQuery = {
    addFilterQuery(s"ancestorCategoryId:$categoryId")
    this
  }
  
  def withOutlet() : ProductQuery = {
    if(request != null && request.getQueryString("outlet").getOrElse("false").toBoolean) {
      addFilterQuery("isOutlet:true")
    }
    this
  }

  def withRedirects() : ProductQuery = {
    if(!(request != null && request.getQueryString("redirects").getOrElse("true").toBoolean)) {
      setParam("redirects", "false")
    }
    this
  }

  def withoutToos() : ProductQuery = {
    addFilterQuery("isToos:false")
    this
  }

  def withCustomParams() : ProductQuery  = {
    SearchCustomParams.map(param =>
        request.queryString.map(qparam =>
          if(qparam._1.equalsIgnoreCase(param)) {
            setParam(qparam._1, qparam._2:_*)
          }
        )
    )
    this
  }

}

private object Query {
  val Score = "score"

  def setFilterQueriesFor(query : SolrQuery)(implicit request: Request[AnyContent]) = {
    val filterQueries = FilterQuery.parseFilterQueries(URLDecoder.decode(request.getQueryString("filterQueries").getOrElse(""), "UTF-8"))

    query.remove("rule.fq")
    filterQueries.foreach(fq => {
      query.add("rule.fq", fq.toString)
     })
    filterQueries
  }
}

/**
 * A search query
 *
 * @param q is the query
 * @param site is the site to search in
 * @param context is the search context
 * @param request is the HTTP request
 *
 */
class ProductSearchQuery(q: String, site: String)(implicit context: Context, request: Request[AnyContent]) extends ProductQuery(q, site) {
  protected override def init() : Unit = {
    super.init()
    setParam("pageType", "search")
  }
}

/**
 * A browse query
 *
 * @param site is the site to search in
 * @param context is the search context
 * @param request is the HTTP request
 *
 */
class ProductBrowseQuery(site: String)(implicit context: Context, request: Request[AnyContent]) extends ProductQuery("*:*", site) {

  protected override def init() : Unit = {
    super.init()
    setParam("pageType", "category")
  }
  
  override def withOutlet() : ProductQuery = {
     request.getQueryString("outlet") match {
      case Some(isOutlet) => addFilterQuery(s"isOutlet:$isOutlet")
      case _ =>
        val isOnSaleParam = "onsale" + context.lang.country
        val isOnSale = request.getQueryString("onsale")
        isOnSale match {
          case Some(isOnSale) => addFilterQuery(s"$isOnSaleParam:$isOnSale")
          case _ => addFilterQuery("isOutlet:false")
        }
     }
     this
   }
}

/**
 * A query to retrieve a single product
 *
 * @param productId is the product id
 * @param site is the site to search in
 * @param context is the search context
 */
class SingleProductQuery(productId : String, site : String)(implicit context: Context) extends SolrQuery("*:*") {
  import Collection._

  private def init() : Unit = {
    setParam("collection", searchCollection.name(context.lang))
    addFilterQuery(s"productId:$productId")
    setRows(1)

    if (site != null) {
       addFilterQuery(s"categoryPath:$site")
     }
  }

  init()

  def withFields(field: String*) : SingleProductQuery = {
    setFields(field:_*)
    this
  }
}

/**
 * A query to retrieve a single category
 *
 * @param categoryIds is the category ids
 * @param site is the site to search in
 * @param context is the search context
 */
class SingleCategoryQuery(categoryIds: Seq[String], site : String)(implicit context: Context) extends SolrQuery("*:*") {
  import Collection._

  private def init() : Unit = {
    setParam("collection", searchCollection.name(context.lang))

    val query = new StringBuilder()

    categoryIds foreach { id =>
      query.append("ancestorCategoryId:")
      query.append(id)
      query.append(" OR ")
    }

    if(categoryIds.length > 0) {
      query.setLength(query.length - 4)
    }

    setQuery(query.toString())
    setRows(0)

    if (site != null) {
      addFilterQuery(s"categoryPath:$site")
    }
  }

  init()

  def withFaceting(field: String = "ancestorCategoryId", limit: Option[Int] = None) : SingleCategoryQuery = {
    setFacet(true)
    addFacetField(field)
    setFacetMinCount(1)

    limit foreach { l =>
      setFacetLimit(l)
    }

    this
  }
}

/**
 * A convenient query to retrieve a field facet on products
 *
 * @param facetField is the facet's field
 * @param site is the site to search in
 * @param context is the search context
 * @param request is the HTTP request
 */
class ProductFacetQuery(facetField: String, site: String)(implicit context: Context, request: Request[AnyContent]) extends SolrQuery("*:*") {
  import Collection._
  import Query._

  private var closeoutSites: Set[String] = Play.current.configuration.getString("sites.closeout").getOrElse("").split(",").toSet
  
  def this(facetField: String)(implicit context: Context, request: Request[AnyContent]) = this(facetField, null)

  private def init() = {
    setParam("collection", searchCollection.name(context.lang))
    setRows(0)
    setFacet(true)
    addFacetField(facetField)
    setFacetMinCount(1)

    if (closeoutSites.contains(site)) {
      addFilterQuery("isRetail:true")
    }
    
    // product params
    addFilterQuery(s"country:${context.lang.country}")
    
    if (site != null) {
      addFilterQuery("categoryPath:" + site)
    }
    if (request != null) {
      
      request.getQueryString("outlet") match {
	    case Some(isOutlet) => {
	      addFilterQuery(s"isOutlet:$isOutlet")
	    }
	    case _ =>
	      val isOnSaleParam = "onsale" + context.lang.country;
	      val isOnSale = request.getQueryString("onsale")
	      isOnSale match {
	        case Some(isOnSale) => addFilterQuery(s"$isOnSaleParam:$isOnSale")
	        case _ => {
	          addFilterQuery("isOutlet:false")
	        }
	      }
	  }
    } else {
      if (closeoutSites.contains(site)) {
        addFilterQuery("isOutlet:false")
      }
    }
  }

  init()

  def withPagination() : ProductFacetQuery = {
    var offset = "0"
    var limit = DefaultPaginationLimit

    for (o <- request.getQueryString("offset")) { offset = o }
    for (l <- request.getQueryString("limit")) { limit = l.toInt }

    setParam("facet.offset", offset)
    setFacetLimit(Math.min(MaxPaginationLimit, limit))
    this
  }

  def withFilterQueries() : ProductFacetQuery = {
    setFilterQueriesFor(this)
    this
  }

  def withPagination(offset: Int, limit: Int) : ProductFacetQuery = {
    setParam("facet.offset", offset.toString)
    setFacetLimit(Math.min(MaxPaginationLimit, limit))
    this
  }

  def withBrand(brandId : String) : ProductFacetQuery = {
    addFilterQuery(s"brandId:$brandId")
    this
  }

  def withAncestorCategory(categoryId : String) : ProductFacetQuery = {
    addFilterQuery(s"ancestorCategoryId:$categoryId")
    this
  }

  def withFacetPrefix(prefix : String) : ProductFacetQuery = {
    setFacetPrefix(prefix)
    this
  }

}


