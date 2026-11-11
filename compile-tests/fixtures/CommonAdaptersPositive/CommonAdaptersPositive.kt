import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.fetch.fetchFunction
import dev.quwac.koyane.node.nodeFunction
import dev.quwac.koyane.node.nodeServer
import dev.quwac.koyane.lambda.awsLambdaFunction

fun fetchEntry() { fetchFunction { env -> resource({ env.getString("VALUE") }) { }; koyane { } } }
fun nodeEntry() { nodeFunction { env -> env.getStringOrNull("VALUE"); koyane { } } }
fun lambdaEntry() { awsLambdaFunction { env -> env.get("NESTED").getString("VALUE"); koyane { } } }
suspend fun serverEntry() = nodeServer { env -> host = env.getString("HOST"); port = 0; resource({ "client" }) { }; koyane { } }
