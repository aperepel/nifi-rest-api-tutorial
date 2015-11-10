import groovy.json.JsonBuilder
import groovyx.net.http.RESTClient

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson
import static groovyx.net.http.ContentType.JSON

@Grab(group='org.codehaus.groovy.modules.http-builder',
        module='http-builder',
        version='0.7.1')

def processorName = 'Save File'
def desiredState = "STOPPED"
def createMissingDirs = true

def nifi = new RESTClient('http://agrande-nifi-1:9090/nifi-api/')

println 'Looking up a component to update...'
def resp = nifi.get(path: 'controller/search-results', query: [q: processorName])
assert resp.status == 200
assert resp.data.searchResultsDTO.processorResults.size() == 1
// println prettyPrint(toJson(resp.data))

def processorId = resp.data.searchResultsDTO.processorResults[0].id
def processGroup= resp.data.searchResultsDTO.processorResults[0].groupId
println "Found the component, id/group:  $processorId/$processGroup"

println 'Preparing to update the flow state...'
resp = nifi.get(path: 'controller/revision')
assert resp.status == 200

// stop the processor before we can update it
println 'Stopping the processor to apply changes...'
def builder = new JsonBuilder()
builder {
    revision {
        clientId 'my awesome script'
        version resp.data.revision.version
    }
    processor {
        id "$processorId"
        state "STOPPED"
    }
}
resp = nifi.put(
        path: "controller/process-groups/$processGroup/processors/$processorId",
        body: builder.toPrettyString(),
        requestContentType: JSON
)
assert resp.status == 200


// create a partial JSON update doc
// TIP: don't name variables same as json keys, simplifies your life
builder {
    revision {
        clientId 'my awesome script'
        version resp.data.revision.version
    }
    processor {
        id "$processorId"
        config {
            properties {
                'Directory' '/tmp/staging'
                'Create Missing Directories' "$createMissingDirs"
            }
        }
    }
}

println "Updating processor...\n${builder.toPrettyString()}"

resp = nifi.put(
        path: "controller/process-groups/$processGroup/processors/$processorId",
        body: builder.toPrettyString(),
        requestContentType: JSON
)
assert resp.status == 200

println "Updated ok."
// println "Got this response back:"
// print prettyPrint(toJson(resp.data))


println 'Bringing the updated processor back online...'
builder {
    revision {
        clientId 'my awesome script'
        version resp.data.revision.version
    }
    processor {
        id "$processorId"
        state "RUNNING"
    }
}
resp = nifi.put(
        path: "controller/process-groups/$processGroup/processors/$processorId",
        body: builder.toPrettyString(),
        requestContentType: JSON
)
assert resp.status == 200
