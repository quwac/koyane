import dev.quwac.koyane.fetch.fetchFunction
import dev.quwac.koyane.node.nodeFunction
import dev.quwac.koyane.node.nodeServer
import dev.quwac.koyane.lambda.awsLambdaFunction

fun fetchEntry() { fetchFunction { env -> env.getString("VALUE") } }
fun nodeEntry() { nodeFunction { env -> env.getString("VALUE") } }
fun lambdaEntry() { awsLambdaFunction { env -> env.getString("VALUE") } }
suspend fun serverEntry() = nodeServer { env -> env.getString("VALUE") }
