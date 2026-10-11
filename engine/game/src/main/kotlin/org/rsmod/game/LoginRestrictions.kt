package org.rsmod.game

public class LoginRestrictions {
    /** When `true`, only staff accounts may log in; everyone else is told an update is running. */
    @Volatile public var maintenance: Boolean = false
}
