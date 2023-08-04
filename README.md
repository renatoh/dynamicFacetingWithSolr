# dynamicFacetingWithSolr

build jar file and put it into:

server/solr/lib -> available to all Solr cores

server/solr/{core-dict}/lib -> available to specific core

start solr in debug mode ./bin/solr -f -a "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=4044"
