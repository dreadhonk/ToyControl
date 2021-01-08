package eu.dreadhonk.apps.toycontrol.service

class PermissionRequired: Exception {
    public val permission: String

    constructor(permission: String): super("Permission required: $permission") {
        this.permission = permission
    }

    constructor(permission: String, message: String): super(message) {
        this.permission = permission
    }

    constructor(permission: String, message: String, cause: Throwable): super(message, cause) {
        this.permission = permission
    }

    constructor(permission: String, cause: Throwable): super("Permission required: $permission", cause) {
        this.permission = permission
    }

}