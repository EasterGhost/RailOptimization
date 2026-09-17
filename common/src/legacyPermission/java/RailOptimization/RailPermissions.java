package RailOptimization;

import net.minecraft.commands.CommandSourceStack;

final class RailPermissions {
	private RailPermissions() {
	}

	static boolean isAdmin(CommandSourceStack source) {
		return source.hasPermission(2);
	}
}
