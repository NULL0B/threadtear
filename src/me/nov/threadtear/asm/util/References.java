package me.nov.threadtear.asm.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import me.nov.threadtear.execution.analysis.ReobfuscateMembers.MappedMember;
import me.nov.threadtear.util.Descriptor;

public class References {

	public static int remapInstructionDescs(Map<String, String> map, AbstractInsnNode ain) {
		if (ain instanceof MethodInsnNode) {
			MethodInsnNode min = (MethodInsnNode) ain;
			min.owner = Descriptor.fixDesc(min.owner, map);
			min.desc = Descriptor.fixDesc(min.desc, map);
		} else if (ain instanceof FieldInsnNode) {
			FieldInsnNode fin = (FieldInsnNode) ain;
			fin.owner = Descriptor.fixDesc(fin.owner, map);
			fin.desc = Descriptor.fixDesc(fin.desc, map);
		} else if (ain instanceof TypeInsnNode) {
			TypeInsnNode tin = (TypeInsnNode) ain;
			tin.desc = Descriptor.fixDesc(tin.desc, map);
		} else if (ain instanceof InvokeDynamicInsnNode) {
			InvokeDynamicInsnNode idin = (InvokeDynamicInsnNode) ain;
			idin.desc = Descriptor.fixDesc(idin.desc, map);
			for (int i = 0; i < idin.bsmArgs.length; i++) {
				Object o = idin.bsmArgs[i];
				if (o instanceof Handle) {
					Handle handle = (Handle) o;
					idin.bsmArgs[i] = new Handle(handle.getTag(), Descriptor.fixDesc(handle.getOwner(), map), handle.getName(), Descriptor.fixDesc(handle.getDesc(), map), handle.isInterface());
				} else if (o instanceof Type) {
					Type type = (Type) o;
					idin.bsmArgs[i] = Type.getType(Descriptor.fixDesc(type.getDescriptor(), map));
				}
				if (idin.bsm != null) {
					idin.bsm = new Handle(idin.bsm.getTag(), Descriptor.fixDesc(idin.bsm.getOwner(), map), idin.bsm.getName(), Descriptor.fixDesc(idin.bsm.getDesc(), map), idin.bsm.isInterface());
				}
			}
		} else if (ain instanceof LdcInsnNode) {
			LdcInsnNode lin = (LdcInsnNode) ain;
			if (lin.cst instanceof Type) {
				Type t = (Type) lin.cst;
				lin.cst = Type.getType(Descriptor.fixDesc(t.getDescriptor(), map));
			}
		} else if (ain instanceof FrameNode) {
			FrameNode fn = (FrameNode) ain;
			for (int i = 0; i < fn.stack.size(); i++) {
				Object o = fn.stack.get(i);
				if (o instanceof String) {
					fn.stack.set(i, Descriptor.fixDesc(o.toString(), map));
				}
			}
			for (int i = 0; i < fn.local.size(); i++) {
				Object o = fn.local.get(i);
				if (o instanceof String) {
					fn.local.set(i, Descriptor.fixDesc(o.toString(), map));
				}
			}
		} else {
			return 0;
		}
		return 1;
	}

	public static int remapMethodReference(HashMap<String, ArrayList<MappedMember>> methods, AbstractInsnNode ain) {
		if (ain instanceof MethodInsnNode) {
			MethodInsnNode min = (MethodInsnNode) ain;
			if (!methods.containsKey(min.owner))
				return 0;
			MappedMember newMapping = methods.get(min.owner).stream().filter(mapped -> mapped.oldName.equals(min.name) && mapped.oldDesc.equals(min.desc)).findFirst().orElse(null);
			if (newMapping == null) {
				// this shouldn't happen, only if the code is referencing a library
				return 0;
			}
			min.name = newMapping.newName;
		} else if (ain instanceof InvokeDynamicInsnNode) {
			InvokeDynamicInsnNode idin = (InvokeDynamicInsnNode) ain;
			for (int i = 0; i < idin.bsmArgs.length; i++) {
				Object o = idin.bsmArgs[i];
				if (o instanceof Handle) {
					Handle handle = (Handle) o;
					String owner = handle.getOwner();
					String name = handle.getName();
					String desc = handle.getDesc();
					idin.bsmArgs[i] = new Handle(handle.getTag(), owner,
							methods.containsKey(owner) ? methods.get(owner).stream().filter(mapped -> mapped.oldName.equals(name) && mapped.oldDesc.equals(desc)).findFirst().get().newName : name, desc,
							handle.isInterface());
				}
			}
			if (idin.bsm != null) {
				String owner = idin.bsm.getOwner();
				String name = idin.bsm.getName();
				String desc = idin.bsm.getDesc();
				idin.bsm = new Handle(idin.bsm.getTag(), owner,
						methods.containsKey(owner) ? methods.get(owner).stream().filter(mapped -> mapped.oldName.equals(name) && mapped.oldDesc.equals(desc)).findFirst().get().newName : name, desc,
						idin.bsm.isInterface());
			}
		} else {
			return 0;
		}
		return 1;
	}

	public static int remapFieldReference(HashMap<String, ArrayList<MappedMember>> fields, AbstractInsnNode ain) {
		if (ain instanceof FieldInsnNode) {
			FieldInsnNode fin = (FieldInsnNode) ain;
			if (!fields.containsKey(fin.owner))
				return 0;
			MappedMember newMapping = fields.get(fin.owner).stream().filter(mapped -> mapped.oldName.equals(fin.name) && mapped.oldDesc.equals(fin.desc)).findFirst().orElse(null);
			if (newMapping == null) {
				// this shouldn't happen, only if the code is referencing a library
				return 0;
			}
			fin.name = newMapping.newName;
			return 1;
		}
		return 0;
	}

	public static void remapMethodType(Map<String, String> map, MethodNode mn) {
		mn.desc = Descriptor.fixDesc(mn.desc, map);
		for (int i = 0; i < mn.exceptions.size(); i++) {
			mn.exceptions.set(i, Descriptor.fixDesc(mn.exceptions.get(i), map));
		}
		mn.tryCatchBlocks.forEach(tcb -> tcb.type = Descriptor.fixDesc(tcb.type, map));
		if (mn.localVariables != null)
			mn.localVariables.forEach(lv -> lv.desc = Descriptor.fixDesc(lv.desc, map));
		if (mn.invisibleAnnotations != null)
			mn.invisibleAnnotations.forEach(a -> a.desc = Descriptor.fixDesc(a.desc, map));
		if (mn.visibleAnnotations != null)
			mn.visibleAnnotations.forEach(a -> a.desc = Descriptor.fixDesc(a.desc, map));
		if (mn.invisibleTypeAnnotations != null)
			mn.invisibleTypeAnnotations.forEach(a -> a.desc = Descriptor.fixDesc(a.desc, map));
		if (mn.visibleTypeAnnotations != null)
			mn.visibleTypeAnnotations.forEach(a -> a.desc = Descriptor.fixDesc(a.desc, map));
		mn.signature = null;
		// cn.attrs.forEach(at -> at.type = Descriptor.fixDesc(at.type, map));
		// TODO remap signature, is actually not necessary. other annotations, etc..
	}

	public static void remapClassType(Map<String, String> map, ClassNode cn) {
		for (int i = 0; i < cn.interfaces.size(); i++) {
			cn.interfaces.set(i, Descriptor.fixDesc(cn.interfaces.get(i), map));
		}
		cn.superName = Descriptor.fixDesc(cn.superName, map);
		if (cn.invisibleAnnotations != null)
			cn.invisibleAnnotations.forEach(a -> a.desc = Descriptor.fixDesc(a.desc, map));
		if (cn.visibleAnnotations != null)
			cn.visibleAnnotations.forEach(a -> a.desc = Descriptor.fixDesc(a.desc, map));
		if (cn.invisibleTypeAnnotations != null)
			cn.invisibleTypeAnnotations.forEach(a -> a.desc = Descriptor.fixDesc(a.desc, map));
		if (cn.visibleTypeAnnotations != null)
			cn.visibleTypeAnnotations.forEach(a -> a.desc = Descriptor.fixDesc(a.desc, map));
		cn.signature = null;
		// TODO more cases
	}

	public static void remapFieldType(Map<String, String> map, FieldNode fn) {
		fn.desc = Descriptor.fixDesc(fn.desc, map);
		if (fn.invisibleAnnotations != null)
			fn.invisibleAnnotations.forEach(a -> a.desc = Descriptor.fixDesc(a.desc, map));
		if (fn.visibleAnnotations != null)
			fn.visibleAnnotations.forEach(a -> a.desc = Descriptor.fixDesc(a.desc, map));
		if (fn.invisibleTypeAnnotations != null)
			fn.invisibleTypeAnnotations.forEach(a -> a.desc = Descriptor.fixDesc(a.desc, map));
		if (fn.visibleTypeAnnotations != null)
			fn.visibleTypeAnnotations.forEach(a -> a.desc = Descriptor.fixDesc(a.desc, map));
		fn.signature = null;
	}
}
