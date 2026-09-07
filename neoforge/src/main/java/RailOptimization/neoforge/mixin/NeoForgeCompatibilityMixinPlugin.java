package RailOptimization.neoforge.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class NeoForgeCompatibilityMixinPlugin implements IMixinConfigPlugin {
	private static final String MIXIN_PACKAGE = "RailOptimization.neoforge.mixin.";
	private static final String BLOCK_STATE = "net/minecraft/world/level/block/state/BlockState";
	private static final String CONDUCTOR_DESCRIPTOR = "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z";
	private static final String HOOK_DESCRIPTOR = "(Lnet/minecraft/world/level/SignalGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z";

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		switch (mixinClassName) {
			case MIXIN_PACKAGE + "RailNeighborSignalCheckerMixin" -> replaceWeakPowerChecks(targetClass);
			case MIXIN_PACKAGE + "RailPathMixin" -> replaceCategoryChecks(targetClass, Set.of("isSameRailWithAxis", "isPoweredRailWithAxis"));
			case MIXIN_PACKAGE + "RailSignalSearcherMixin" -> replaceCategoryChecks(targetClass, Set.of("findPoweredRailSignalAt"));
			case MIXIN_PACKAGE + "RailUpdateNotifierMixin" -> replaceNotificationSources(targetClass);
			default -> throw new IllegalStateException("Unknown NeoForge compatibility mixin: " + mixinClassName);
		}
	}

	private static void replaceWeakPowerChecks(ClassNode targetClass) {
		int replacements = 0;
		for (MethodNode method : targetClass.methods) {
			if (!method.name.equals("belowStateWhenNoNeighborSignal") && !method.name.equals("belowStateWhenNoNeighborSignalInChunk")) continue;
			for (var instruction : method.instructions.toArray()) {
				if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(BLOCK_STATE)
						|| !call.name.equals("isRedstoneConductor") || !call.desc.equals(CONDUCTOR_DESCRIPTOR)) continue;
				int directionSlot = directionSlot(method, method.instructions.indexOf(call));
				method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, directionSlot));
				call.name = "shouldCheckWeakPower";
				call.desc = HOOK_DESCRIPTOR;
				method.maxStack++;
				replacements++;
			}
		}
		if (replacements != 2) throw new IllegalStateException("Expected both rail signal paths, found " + replacements);
	}

	private static void replaceCategoryChecks(ClassNode targetClass, Set<String> methodNames) {
		int replacements = 0;
		for (MethodNode method : targetClass.methods) {
			if (!methodNames.contains(method.name)) continue;
			int matches = 0;
			for (var instruction : method.instructions.toArray()) {
				if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(BLOCK_STATE)
						|| !call.name.equals("is") || !call.desc.equals("(Ljava/lang/Object;)Z")) continue;
				call.setOpcode(Opcodes.INVOKESTATIC);
				call.owner = "RailOptimization/neoforge/NeoForgeRailCompatibility";
				call.name = "isCompatibleRail";
				call.desc = "(L" + BLOCK_STATE + ";Lnet/minecraft/world/level/block/PoweredRailBlock;)Z";
				call.itf = false;
				matches++;
			}
			if (matches != 1) throw new IllegalStateException("Expected one rail category check in " + method.name + ", found " + matches);
			replacements++;
		}
		if (replacements != methodNames.size()) throw new IllegalStateException("Missing rail category methods in " + targetClass.name);
	}

	private static void replaceNotificationSources(ClassNode targetClass) {
		int replacements = 0;
		for (MethodNode method : targetClass.methods) {
			if (!method.name.equals("notifyMain") && !method.name.equals("notifySupport") && !method.name.equals("notifyOuter")) continue;
			if ((method.access & Opcodes.ACC_STATIC) == 0 || !method.desc.startsWith(
					"(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/Block;LRailOptimization/RailChangeList;I")) {
				throw new IllegalStateException("Unexpected notification signature: " + method.name + method.desc);
			}
			InsnList source = new InsnList();
			source.add(new VarInsnNode(Opcodes.ALOAD, 2));
			source.add(new VarInsnNode(Opcodes.ILOAD, 3));
			source.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "RailOptimization/RailChangeList", "state", "(I)L" + BLOCK_STATE + ";", false));
			source.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_STATE, "getBlock", "()Lnet/minecraft/world/level/block/Block;", false));
			source.add(new VarInsnNode(Opcodes.ASTORE, 1));
			method.instructions.insert(source);
			method.maxStack = Math.max(method.maxStack, 2);
			replacements++;
		}
		if (replacements != 3) throw new IllegalStateException("Expected three rail notification paths, found " + replacements);
	}

	private static int directionSlot(MethodNode method, int instructionIndex) {
		int slot = -1;
		if (method.localVariables == null) throw new IllegalStateException("Missing local variables in " + method.name);
		for (LocalVariableNode local : method.localVariables) {
			if (local.desc.equals("Lnet/minecraft/core/Direction;")
					&& method.instructions.indexOf(local.start) <= instructionIndex && instructionIndex < method.instructions.indexOf(local.end)) {
				if (slot != -1) throw new IllegalStateException("Ambiguous direction in " + method.name);
				slot = local.index;
			}
		}
		if (slot == -1) throw new IllegalStateException("Missing direction in " + method.name);
		return slot;
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() { return null; }

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() { return null; }

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
