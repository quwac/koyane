import dev.quwac.koyane.runtime.Environment
class Handle(val native: Any)
fun handle(environment: Environment): Handle = environment.get("DB", ::Handle)
