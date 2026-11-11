import dev.quwac.koyane.runtime.Environment
class Handle(val native: Any)
fun handle(environment: Environment): String = environment.get("DB", ::Handle)
