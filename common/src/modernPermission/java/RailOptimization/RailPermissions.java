package RailOptimization;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

final class RailPermissions {
	private RailPermissions() {
	}

	static boolean isAdmin(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
	}
}
