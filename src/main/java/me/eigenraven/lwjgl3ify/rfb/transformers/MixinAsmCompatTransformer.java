package me.eigenraven.lwjgl3ify.rfb.transformers;

import static org.objectweb.asm.Opcodes.*;

import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;

/**
 * Mixin 0.7.x, the version 1.8.9 mods (including lwjgl3ify itself) bundle, carries its own copy of ASM 5 relocated to
 * {@code org.spongepowered.asm.lib}. That ASM rejects any class file newer than Java 8, so as soon as Mixin needs the
 * hierarchy of a JDK class (e.g. to compute stack frames), it fails on Java 9+.
 * <p>
 * This removes the class version check from that relocated {@code ClassReader}. ASM 5 can otherwise read JDK class
 * files just fine for Mixin's purposes: newer attributes (NestMembers, Record, ...) are kept as unknown attributes and no
 * new bytecode instructions were added since Java 7.
 */
public class MixinAsmCompatTransformer implements RfbClassTransformer {

    private static final Logger LOGGER = LogManager.getLogger("lwjgl3ify");

    public static final String[] RELOCATED_CLASS_READERS = new String[] { "org.spongepowered.asm.lib.ClassReader" };

    @Pattern("[a-z0-9-]+")
    @Override
    public @NotNull String id() {
        return "mixin-asm-compat";
    }

    @Override
    public boolean shouldTransformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull Context context,
        @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        if (!classNode.isPresent()) {
            return false;
        }
        for (final String target : RELOCATED_CLASS_READERS) {
            if (target.equals(className)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean transformClassIfNeeded(@NotNull ExtensibleClassLoader classLoader, @NotNull Context context,
        @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        if (classNode.getNode() == null) {
            return false;
        }
        int patched = 0;
        for (final MethodNode method : classNode.getNode().methods) {
            if (!"<init>".equals(method.name) || !"([BII)V".equals(method.desc) || method.instructions == null) {
                continue;
            }
            // if (readShort(off + 6) > Opcodes.V1_8) throw new IllegalArgumentException();
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode call) || !"readShort".equals(call.name)) {
                    continue;
                }
                final AbstractInsnNode limit = call.getNext();
                if (limit == null || limit.getNext() == null || limit.getNext()
                    .getOpcode() != IF_ICMPLE) {
                    continue;
                }
                final boolean isJava8Limit = (limit instanceof IntInsnNode intInsn && intInsn.operand == V1_8)
                    || (limit instanceof LdcInsnNode ldc && Integer.valueOf(V1_8)
                        .equals(ldc.cst));
                if (!isJava8Limit) {
                    continue;
                }
                method.instructions.set(limit, new IntInsnNode(SIPUSH, Short.MAX_VALUE));
                patched++;
                break;
            }
        }
        if (patched == 0) {
            LOGGER.warn("Could not find the class version check in {}, Mixin may fail to read Java 9+ classes", className);
            return false;
        }
        LOGGER.info("Allowed {} to read class files newer than Java 8", className);
        return true;
    }
}
