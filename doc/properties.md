- [CTIA Configuration Properties](#sec-1)
  - [Auth](#sec-1-1)
    - [Threatgrid](#sec-1-1-1)
    - [Static](#sec-1-1-2)
  - [Access Control](#sec-1-2)
  - [HTTP](#sec-1-3)
    - [Access Control](#sec-1-3-1)
    - [JWT](#sec-1-3-2)
    - [Show](#sec-1-3-3)
    - [Bulk](#sec-1-3-4)
  - [Events](#sec-1-4)
  - [nRepl](#sec-1-5)
  - [Hooks](#sec-1-6)
    - [RedisMQ](#sec-1-6-1)
    - [Redis](#sec-1-6-2)
    - [Generic](#sec-1-6-3)
  - [Metrics](#sec-1-7)
    - [Console](#sec-1-7-1)
    - [JMX](#sec-1-7-2)
    - [Riemann](#sec-1-7-3)
  - [Store](#sec-1-8)
    - [ES](#sec-1-8-1)

# CTIA Configuration Properties<a id="sec-1"></a>

## Auth<a id="sec-1-1"></a>

Auth related configuration, CTIA supports choosing an auth Identity provider among:

`threatgrid`, `static`, `allow-all`

JWT authentication is also supported, see the `JWT` section for more details.

using `allow-all` requires no configuration, users will be identified as `Unknown` and belong to `Unknown group` it is preferably intended for development puprposes

available options vary depending on the choosen authentication provider:

| Property       | Description                    | Possible values                    |
|-------------- |------------------------------ |---------------------------------- |
| ctia.auth.type | set CTIA auth provider backend | `allow-all`  `static` `threatgrid` |

### Threatgrid<a id="sec-1-1-1"></a>

Authenticate using Threat GRID api keys

| Property                        | Description                 | Possible values |
|------------------------------- |--------------------------- |--------------- |
| ctia.auth.threatgrid.cache      | enable caching token checks | `true` `false`  |
| ctia.auth.threatgrid.whoami-url | set the token check url     | url string      |

### Static<a id="sec-1-1-2"></a>

Authenticate using a configurable static set of credentials, users will share the same Authorization token.

| Property                | Description                  | Possible values |
|----------------------- |---------------------------- |--------------- |
| ctia.auth.static.secret | set the Authorization secret | string          |
| ctia.auth.static.name   | set the login identity       | string          |
| ctia.auth.static.group  | set the login group          | string          |

## Access Control<a id="sec-1-2"></a>

Setup entity access control settings

| Property                        | Description                                                | Possible values                |
|------------------------------- |---------------------------------------------------------- |------------------------------ |
| ctia.access-control.min-tlp     | set the minimum TLP value for posting a document           | `white` `green`  `amber` `red` |
| ctia.access-control.default-tlp | set the TLP for a newly posted entity if none is specified | `white` `green` `amber` `red`  |

## HTTP<a id="sec-1-3"></a>

HTTP server related configuration

| Property              | Description                                           | Possible values |
|--------------------- |----------------------------------------------------- |--------------- |
| ctia.http.enabled     | enable the http server                                | `true` `false`  |
| ctia.http.port        | set the listening port                                | number          |
| ctia.http.min-threads | set the min number of threads to handle HTTP requests | number          |
| ctia.http.max-threads | set the max number of threads to handle HTTP requests | number          |

### Access Control<a id="sec-1-3-1"></a>

CORS access control settings, allow CTIA API access from a different domain see: <https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS>

| Property                               | Description                           | Possible values                      |
|-------------------------------------- |------------------------------------- |------------------------------------ |
| ctia.http.access-control.allow-origin  | set the CORS allow origins config     | a coma separated list of regexps     |
| ctia.http.access-control.allow-methods | set the CORS allow methods config     | a comma separated list of HTTP verbs |
| ctia.http.dev-reload                   | Development Only, reload code on edit | `true` `false`                       |

### JWT<a id="sec-1-3-2"></a>

Configure JWT authentication support, see: <https://jwt.io/>

| Property                        | Description               | Possible values      |
|------------------------------- |------------------------- |-------------------- |
| ctia.http.jwt.enabled           | enable JWT auth support   | `true` `false`       |
| ctia.http.jwt.public-key-path   | set the JWT key path      | string (a full path) |
| ctia.http.jwt.local-storage-key | set JWT local storage key | string (a full path) |

### Show<a id="sec-1-3-3"></a>

Configure how CTIA is hosted, setting those values correctly is mandatory as it defines how entity ids are generated.

| Property                   | Description                                   | Possible values |
|-------------------------- |--------------------------------------------- |--------------- |
| ctia.http.show.protocol    | is this instance hosted through http or https | `http` `https`  |
| ctia.http.show.hostname    | set the hostname used to access this instance | string          |
| ctia.http.show.path-prefix | set a path prefix if CTIA is not exposed at / | string          |
| ctia.http.show.port        | set the exposed http port                     | number          |

### Bulk<a id="sec-1-3-4"></a>

Set limits for entity bulk operations

| Property                | Description                                                                   | Possible values |
|----------------------- |----------------------------------------------------------------------------- |--------------- |
| ctia.http.bulk.max-size | Set the maximum number of entities one can post using a single bulk operation | number          |

## Events<a id="sec-1-4"></a>

Event related configuration

| Property        | Description           | Possible values |
|--------------- |--------------------- |--------------- |
| ctia.events.log | enable CTIA Event log | `true` `false`  |

## nRepl<a id="sec-1-5"></a>

setup clojure nrepl support, for development

| Property           | Description                      | Possible values |
|------------------ |-------------------------------- |--------------- |
| ctia.nrepl.enabled | enable CTIA nrepl                | `true` `false`  |
| ctia.nrepl.port    | set the port to access the nrepl | number          |

## Hooks<a id="sec-1-6"></a>

### RedisMQ<a id="sec-1-6-1"></a>

setup pushing events to redisMQ

| Property                     | Description                          | Possible values |
|---------------------------- |------------------------------------ |--------------- |
| ctia.hook.redismq.queue-name | set the queue name                   | string          |
| ctia.hook.redismq.port       | set the port of the redisMQ instance | number          |
| ctia.hook.redismq.timeout-ms | event pushing timeout                | number          |
| ctia.hook.redismq.max-depth  |                                      | number          |

### Redis<a id="sec-1-6-2"></a>

setup pushing events to a channel on a redis instance

| Property                     | Description                           | Possible values |
|---------------------------- |------------------------------------- |--------------- |
| ctia.hook.redis.host         | set the redis instance host           | string          |
| ctia.hook.redis.port         | set the redis instace port            | number          |
| ctia.hook.redis.timeout-ms   | event pushing timeout                 | number          |
| ctia.hook.redis.channel-name | the chan where events shall be pushed | string          |

### Generic<a id="sec-1-6-3"></a>

call your own functions on any CTIA event, these functions need to be available on the classpath

| Property                 | Description                                            | Possible values |
|------------------------ |------------------------------------------------------ |--------------- |
| ctia.hooks.before-create | call a function before entity creation                 | string          |
| ctia.hooks.after-create  | call a function when an entity has been created        | string          |
| ctia.hooks.before-update | call a function before updating an entity              | string          |
| ctia.hooks.after-update  | call a function when an entity has been updated        | string          |
| ctia.hooks.before-delete | call a function when an entity is about to get deleted | string          |
| ctia.hooks.after-delete  | call a function when an entity has been deleted        | string          |

## Metrics<a id="sec-1-7"></a>

setup CTIA performance metrics reporting

### Console<a id="sec-1-7-1"></a>

Periodicaly output performance metrics to the console output

| Property                      | Description                                                    | Possible values |
|----------------------------- |-------------------------------------------------------------- |--------------- |
| ctia.metrics.console.enabled  | periodically output performance metrics to the console         | boolean         |
| ctia.metrics.console.interval | how often shall the metrics be displayed on the console output | seconds         |

### JMX<a id="sec-1-7-2"></a>

Setup JMX metrics reporting

| Property                 | Description | Possible values |
|------------------------ |----------- |--------------- |
| ctia.metrics.jmx.enabled | enable JMX  | boolean         |

### Riemann<a id="sec-1-7-3"></a>

Setup Riemann metrics reporting

| Property                      | Description                      | Possible values |
|----------------------------- |-------------------------------- |--------------- |
| ctia.metrics.riemann.enabled  | enable riemann metrics reporting | boolean         |
| ctia.metrics.riemann.host     | riemann instance host            | string          |
| ctia.metrics.riemann.port     | riemann instance port            | number          |
| ctia.metrics.riemann.interval | how often to push metrics        | seconds         |

## Store<a id="sec-1-8"></a>

Each entity type is stored using a separate Store that shares nothing with the others. it is possible to use different data stores depending on the entity type. currently CTIA has store implementations available only for Elasticsearch.

start by selecting a store implementation for your entity type, then customize its settings

available entities are:

`actor` `campaign` `coa` `event` `data-table` `exploit-target` `feedback` `identity` `incident` `indicator` `judgement` `relationship` `sighting` `ttp`

| Property            | Description                                      | Possible values |
|------------------- |------------------------------------------------ |--------------- |
| ctia.store.<entity> | select a store implementation for a given entity | es              |

### ES<a id="sec-1-8-1"></a>

Set ES Store implementation settings, one can set defaults for all ES stores using `default` as entity

| Property                         | Description                                                   | Possible values |
|-------------------------------- |------------------------------------------------------------- |--------------- |
| ctia.store.es.[entity].host      | ES instance host                                              | string          |
| ctia.store.es.[entity].port      | ES instance port                                              | port            |
| ctia.store.es.[entity].indexname | ES index name to use                                          | string          |
| ctia.store.es.[entity].refresh   | wether to trigger an index refresh after each write operation | boolean         |
| ctia.store.es.[entity].replicas  | how many replicas to setup at index creation                  | number          |
| ctia.store.es.[entity].shards    | how many shards to setup at index creation                    | number          |
