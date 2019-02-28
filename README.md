This application provides the internal REST API to manage event engine tasks, a term borrowed 
from the Kapacitor tasks that are ultimately managed. Specifically this application translates 
JSON, config-driven event scenarios into the rich, expressive TICKscript.

It uses the event engine discovery logic in https://github.com/racker/salus-event-engine-common 
to locate the instance of Kapacitor where a given task should be created or deleted. 
Like the other Salus applications, it uses MySQL via Spring JPA to persist the 
Salus event task definitions.

## Interfacing with this REST service

Adding the following `client` dependency to the calling application's `pom.xml`
will provide the Java model classes used for interacting with the REST API.

```xml
        <dependency>
            <groupId>com.rackspace.salus</groupId>
            <artifactId>salus-event-engine-management</artifactId>
            <version>${event-engine.version}</version>
            <classifier>client</classifier>
            <exclusions>
                <!-- There doesn't seem to be a way to exclude all the normal transitives via the
                  client jar build, so we'll exclude them all here -->
                <exclusion>
                    <groupId>*</groupId>
                    <artifactId>*</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

```