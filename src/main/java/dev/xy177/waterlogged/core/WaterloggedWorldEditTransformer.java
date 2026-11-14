package dev.xy177.waterlogged.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import top.outlands.foundation.IExplicitTransformer;

/** Optional transformer for WorldEdit's block copy operation. */
@SuppressWarnings("unused")
public final class WaterloggedWorldEditTransformer implements IExplicitTransformer {
    private static final String VECTOR = "Lcom/sk89q/worldedit/Vector;";
    private static final String BRIDGE = "dev/xy177/waterlogged/core/WaterloggedWorldEditBridge";
    private static final int DEST_LOCAL = 50;
    private static final int TARGET_LOCAL = 51;
    private static final int BLOCK_LOCAL = 52;
    private static final int RESULT_LOCAL = 53;

    @Override
    public byte[] transform(byte[] bytes) {
        try {
            ClassNode type = new ClassNode(Opcodes.ASM9);
            new ClassReader(bytes).accept(type, 0);
            if (type.name.equals("com/sk89q/worldedit/forge/ForgeWorld")) {
                return transformForgeWorld(type);
            }
            if (!type.name.equals("com/sk89q/worldedit/function/block/ExtentBlockCopy")) {
                return bytes;
            }
            boolean changed = false;
            for (MethodNode method : type.methods) {
                if (!"apply".equals(method.name)
                        || !method.desc.startsWith("(" + VECTOR)) continue;
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                        instruction != null; instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (!"setBlock".equals(call.name)
                            || !(call.getOpcode() == Opcodes.INVOKEINTERFACE
                                    || call.getOpcode() == Opcodes.INVOKEVIRTUAL)
                            || !call.desc.startsWith("(" + VECTOR)) continue;

                    InsnList before = new InsnList();
                    before.add(new VarInsnNode(Opcodes.ASTORE, BLOCK_LOCAL));
                    before.add(new VarInsnNode(Opcodes.ASTORE, TARGET_LOCAL));
                    before.add(new VarInsnNode(Opcodes.ASTORE, DEST_LOCAL));
                    before.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    before.add(new FieldInsnNode(Opcodes.GETFIELD, type.name, "source",
                            "Lcom/sk89q/worldedit/extent/Extent;"));
                    before.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "begin",
                            "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
                    before.add(new VarInsnNode(Opcodes.ALOAD, DEST_LOCAL));
                    before.add(new VarInsnNode(Opcodes.ALOAD, TARGET_LOCAL));
                    before.add(new VarInsnNode(Opcodes.ALOAD, BLOCK_LOCAL));
                    method.instructions.insertBefore(call, before);

                    InsnList after = new InsnList();
                    after.add(new VarInsnNode(Opcodes.ISTORE, RESULT_LOCAL));
                    after.add(new VarInsnNode(Opcodes.ALOAD, DEST_LOCAL));
                    after.add(new VarInsnNode(Opcodes.ALOAD, TARGET_LOCAL));
                    after.add(new VarInsnNode(Opcodes.ILOAD, RESULT_LOCAL));
                    after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "finish",
                            "(Ljava/lang/Object;Ljava/lang/Object;Z)V", false));
                    after.add(new VarInsnNode(Opcodes.ILOAD, RESULT_LOCAL));
                    method.instructions.insert(call, after);
                    changed = true;
                }
            }
            if (!changed) {
                System.err.println("[Waterlogged] WorldEdit ExtentBlockCopy loaded, but no setBlock call matched");
                return bytes;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            type.accept(writer);
            System.out.println("[Waterlogged] Injected waterlogged copy into WorldEdit ExtentBlockCopy");
            return writer.toByteArray();
        } catch (Throwable error) {
            System.err.println("[Waterlogged] WorldEdit transformer failed: " + error);
            return bytes;
        }
    }

    private byte[] transformForgeWorld(ClassNode type) {
        for (MethodNode method : type.methods) {
            if (!"setBlock".equals(method.name)
                    || !method.desc.equals("(Lcom/sk89q/worldedit/Vector;"
                            + "Lcom/sk89q/worldedit/blocks/BaseBlock;Z)Z")) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!call.owner.equals("net/minecraft/world/chunk/Chunk")
                        || !call.desc.equals("(Lnet/minecraft/util/math/BlockPos;"
                                + "Lnet/minecraft/block/state/IBlockState;)"
                                + "Lnet/minecraft/block/state/IBlockState;")) continue;
                AbstractInsnNode store = call.getNext();
                while (store != null && !(store instanceof VarInsnNode
                        && store.getOpcode() == Opcodes.ASTORE
                        && ((VarInsnNode) store).var == 12)) {
                    store = store.getNext();
                }
                if (store == null) break;
                InsnList afterWrite = new InsnList();
                afterWrite.add(new VarInsnNode(Opcodes.ALOAD, 4));
                afterWrite.add(new VarInsnNode(Opcodes.ALOAD, 9));
                afterWrite.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE,
                        "afterWorldEditBlockWrite",
                        "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V", false));
                method.instructions.insert(store, afterWrite);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                type.accept(writer);
                System.out.println("[Waterlogged] Injected deferred fluid copy into WorldEdit ForgeWorld");
                return writer.toByteArray();
            }
        }
        System.err.println("[Waterlogged] WorldEdit ForgeWorld loaded, but chunk write was not matched");
        return typeBytes(type);
    }

    private byte[] typeBytes(ClassNode type) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        return writer.toByteArray();
    }
}
