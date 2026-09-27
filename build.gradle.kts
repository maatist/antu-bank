// Build raíz de Antu Bank.
// La configuración compartida vive en convention plugins (build-logic).
// Este archivo se mantiene mínimo a propósito.

tasks.register("printModules") {
    group = "help"
    description = "Lista los módulos del monorepo."
    doLast {
        subprojects.forEach { println(it.path) }
    }
}
