# Business Case 

A precise  product search and the capability of refining the search results using facets  is an integral part of the product search.

Which facet is calculated and shown to the customer on what search is often maintained manually.For example a number of facets , which are valid across all or most of the products, such as price, categories- brands, etc.,  are applied to all searches. More facets are then maintained on category-level, for searches within a category. These facets are then refined further down on the category-hierarchy.

This is not only labour-some to maintain, it also does not work very well for full-text-searches where no category has been selected in advanced. Such a search returns  search-results across a wide range of different products. For example a  search  for “usb cable” (without the double quotes) on an electronics stores will return results across very different type or products leading to very messy facets. 

Would it be nice if the facet we apply to a search were  bespoke to each individual search and not depending on any static context such as the category I am searching in.
If an attribute is precent on a high percentage of the search-result, we want to use it as a facet, if an attribute is sparse, we do not use it as a facet.

If an attribute is present in a significant proportion of the search results, we use it as a facet, if an attribute is sparse, we do not use it as a facet.
This not only saves the manual maintenance of rules where and when which fact is used, it also only shows facets, which are meaningful to every single search.

This approach not only eliminates the need for manual upkeep of rules when wich facet is applied, it also ensures that only facets relevant to each individual search are displayed.

# Technical Implementation

## 1. Identify Attributes
 We need a way to identify all attribute we want to use the dynamic faceting on, the easiest is to use a naming convention e.g. all attribute which should be considered could start with a certain prefix like “df” df_powerconsumption_int.  Alternatively the list of all field-nams could be maintained in a property, this might give more flexibility but also adding the work of maintaining the list of attributes.

## 2. Introduce Helper-Field on Solr-Document
We introduce a helper field on each Solr document, in which we store a list of all attributes of this document, which we use for the dynamic attributes. Let’s call this helper field dynamic_facet_fields_string_mv, it is mult-value since we want to hold a list of a fields for the dynamic facetting.

So we take a document which looks like this:

```
{
  "code_string": 123,
  "price_double": 123.45,
  "df_maxpowerconsumption_int": 200,
  "df_minpowerconsumption_int": 5,
  "df_weight_double": 1560
}
```
..and add the field dynamic_facet_fields_string_mv to it:
```
{
  "code_string": 123,
  "price_double": 123.45,
  "df_maxpowerconsumption_int": 200,
  "df_minpowerconsumption_int": 5,
  "df_weight_double": 1560,
  "dynamic_facet_fields_string_mv": [
    "df_maxpowerconsumption_int",
    "df_minpowerconsumption_int",
    "df_weight_double"
  ]
}
```
This happens within Solr using a UpdateRequestProcessorFactory, here the implemenation:
[AddDynamicFacetFieldProcessorFactory.java](https://github.com/renatoh/dynamicFacetingWithSolr/blob/main/src/main/java/custom/AddDynamicFacetFieldProcessorFactory.java)

## 3. Do Sub-Query to Determine Fields for Faceting
The third and last step, is to run a sub-query for each search for wich we want to use the dynamics facets. This sub-query does not fetch any documetns, the only thing it does is to facet on the helper-field dynamic_faceting_string_mv, the counts on the helper-field dynamic_faceting_string_mv will tell us, how frequently which attribute is present in the search result. Let me illustrate this with another exmaple:


In this snipped from a response of the sub-query, we see that in total the sub-query returns 328 documents. This count is the same as for the main-query since we do apply the same filters. From the facet counts on the facet for the field dynamic_facet_fields_string_mv, we can now calculated coverate of each field within the search result, e.g. df_weight_double has a high coverage 0.618 (203/328), the other two fields df_maxpowerconsumption_int and df_maxpowerconsumption_int have a low coverage 0.137(45/328)
```
response":{"numFound":328,"start":0,"numFoundExact":true,"docs

  "facet_fields":{
      "dynamic_facet_fields_string_mv":[
        "df_weight_double",203,
        "df_maxpowerconsumption_int",45,
        "df_minpowerconsumption_int",45
        ]}
``` 
We take now all the facet-names with coverage above a threshold e.g. 0.3 and add these as facet to the main query. In our example we would only ad facet.field=df_weight_double to it.
This is done by implementing an onw SearchComponent:

[AddDynamicFacetsSearchComponent.java](https://github.com/renatoh/dynamicFacetingWithSolr/blob/main/src/main/java/custom/AddDynamicFacetsSearchComponent.java)

## 4. Configuring Solr
The only thing left is now to back these two classes to a jar, deploy it to Solr, and to register the custom UpdateRequestProcessorFactory and SearchComponent in the solrconfig.xml

[solrconfig.xml](https://github.com/renatoh/dynamicFacetingWithSolr/blob/main/resources/solrconfig.xml)

# Build Instructions


build jar file and put it into:

server/solr/lib -> available to all Solr cores

server/solr/{core-dict}/lib -> available to specific core

start solr in debug mode ./bin/solr -f -a "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=4044"
