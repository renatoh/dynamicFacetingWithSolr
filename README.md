# Business Case 

A precise product search and the capability to refine search results using facets are integral parts of any online shop.
Facets are often used across hundreds of different attributes, but it is not efficient and sometimes simply impossible to calculate all the facets for each and every search.

Often, the solution to this problem is to manually maintain facets on the category tree. Facets that are valid across all products, such as price, categories, brands, etc., are maintained at the root category level, while facets for products of a specific category are configured further down the category tree.

This is not only tedious to maintain, but it also does not work very well for full-text searches where no category has been selected in advance. Such a search returns results across a wide range of different products. For example, a search for "usb cable" (without the double quotes) on an electronics store will return results across very different types of products, leading to very messy facets. 

Wouldn't it be nice if the facets applied to a search were tailored to each individual search, rather than relying on any static context such as the category I am searching within? If an attribute is present in a high percentage of the products within the search, we would want to use it as a facet, if an attribute is sparse, we would not use it as a facet.

This approach not only eliminates the need for manual upkeep of rules when which facet is applied, it also ensures that only facets relevant to each individual search are calculated and displayed.

# Technical Implementation

## 1. Identify Fields
 We need a way to identify all fields we want to use the dynamic faceting on, the easiest is to use a naming convention e.g. all fields which should be considered could start with a certain prefix like "df_" e.g. df_maxpowerconsumption_d. Alternatively the list of all field-names could be maintained in a property, this might give more flexibility but comes at the cost of maintaining this property.

## 2. Introduce Helper-Field on Solr-Document
We introduce a helper field on each Solr document, in which we store a list of all field-names of this document for which we want to use the dynamic faceting. Let's call this helper field dynamic_facet_fields_ss, it is multi-value since we want to hold a list of field-names.

So we take a document which looks like this:
```
{
  "code_s": 123,
  "price_d": 123.45,
  "df_maxpowerconsumption_d": 200,
  "df_length_i": 5,
  "df_weight_d": 1560
}
```
..and add the field dynamic_facet_fields_ss to it:
```
{
  "code_s": 123,
  "price_d": 123.45,
  "df_maxpowerconsumption_d": 200,
  "df_length_i": 5,
  "df_weight_d": 1560,
  "dynamic_facet_fields_ss": [
    "df_maxpowerconsumption_d",
    "df_length_i",
    "df_weight_d"
  ]
}
```
This happens within Solr using an UpdateRequestProcessorFactory, here the implementation:
[AddDynamicFacetFieldProcessorFactory.java](https://github.com/renatoh/dynamicFacetingWithSolr/blob/main/src/main/java/custom/AddDynamicFacetFieldProcessorFactory.java)

## 3. Do Sub-Query to Determine Fields for Faceting
The next step is to run a sub-query for each search for which we want to use the dynamic facets. This sub-query does not fetch any documents, the only thing it does is to facet on the helper-field dynamic_facet_fields_ss, the facet-counts on the helper-field tells us, how frequently the fields are present on the search result before actually doing the search. Let me illustrate this with an example:

In this snipped from a response of the sub-query, we see that in total the sub-query returns 328 documents. The count will be the same as for the main-query since we do apply the same filters. With the facet counts on the facet for the field dynamic_facet_fields_ss, we can calculated the coverage of each field within the search result. The field df_weight_d has a high coverage 0.618 (203/328), the other two fields df_maxpowerconsumption_d and df_length_i have a low coverage.
```
response":{"numFound":328,"start":0,"numFoundExact":true,"docs

  "facet_fields":{
      "dynamic_facet_fields_ss":[
        "df_weight_d",203,
        "df_maxpowerconsumption_d",45,
        "df_length_i",12
        ]}
``` 
We take now all the field-names with a coverage above a threshold e.g. 0.3 and add these as facet to the main query. In our example we would only add facet.field=df_weight_d to it.
This is done within our own SearchComponent:
[AddDynamicFacetsSearchComponent.java](https://github.com/renatoh/dynamicFacetingWithSolr/blob/main/src/main/java/custom/AddDynamicFacetsSearchComponent.java)

## 4. Configuring Solr
The only thing left is now to pack these two classes into a jar, deploy it to Solr, and to register the custom UpdateRequestProcessorFactory and SearchComponent in the solrconfig.xml
[solrconfig.xml](https://github.com/renatoh/dynamicFacetingWithSolr/blob/main/resources/solrconfig.xml)

# Build & Deploy

Build the jar:
```
./gradlew jar
```

Deploy the jar to Solr by placing it in one of:

- `server/solr/lib` — available to all Solr cores
- `server/solr/{core}/lib` — available to a specific core

Start Solr in debug mode:
```
./bin/solr -f -a "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=4044"
```

# Local Testing with Docker Compose

For quick local testing, a Docker Compose setup is included that starts Solr and indexes sample data automatically:
```
docker compose up
```

Solr will be available at http://localhost:8983. Debug port is exposed on 5005.
