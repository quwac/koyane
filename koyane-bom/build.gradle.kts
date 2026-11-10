plugins {
    `java-platform`
}

dependencies {
    constraints {
        api(project(":koyane-core"))
        api(project(":koyane-http"))
        api(project(":koyane-content-negotiation"))
        api(project(":koyane-bearer-authentication"))
        api(project(":koyane-cors"))
        api(project(":koyane-error-handling"))
        api(project(":koyane-kotlinx-datetime"))
        api(project(":koyane-kotlinx-serialization"))
        api(project(":koyane-logging"))
        api(project(":koyane-testing"))
        api(project(":koyane-workers"))
        api(project(":koyane-node"))
        api(project(":koyane-fetch"))
        api(project(":koyane-lambda"))
    }
}
