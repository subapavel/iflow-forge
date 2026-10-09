package io.poxray.integration;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal reader of a Java class file: class name, superclass, interfaces and all UTF-8 constants (class names,
 * descriptors, strings), enough to recognise mapping programs and the APIs a class uses without decompiling.
 *
 * @param className  internal name, e.g. {@code cz/acme/OrderMapping}
 * @param superclass internal name of the superclass (null for java/lang/Object's own file)
 * @param interfaces internal names of the implemented interfaces
 * @param utf8       all UTF-8 constants of the constant pool
 */
record ClassFileInfo(String className, String superclass, List<String> interfaces, List<String> utf8) {

    static ClassFileInfo parse(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 0xCAFEBABE) {
                throw new IllegalArgumentException("not a class file");
            }
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            int count = in.readUnsignedShort();
            Map<Integer, String> utf8 = new HashMap<>();
            Map<Integer, Integer> classes = new HashMap<>();
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8.put(i, in.readUTF());
                    case 7 -> classes.put(i, in.readUnsignedShort());
                    case 8, 16, 19, 20 -> in.readUnsignedShort();
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> in.readInt();
                    case 5, 6 -> {
                        in.readLong();
                        i++; // 8-byte constants take two slots
                    }
                    case 15 -> {
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                    }
                    default -> throw new IllegalArgumentException("unknown constant pool tag " + tag);
                }
            }
            in.readUnsignedShort(); // access flags
            String name = utf8.get(classes.get(in.readUnsignedShort()));
            int superIndex = in.readUnsignedShort();
            String superName = superIndex == 0 ? null : utf8.get(classes.get(superIndex));
            int interfaceCount = in.readUnsignedShort();
            List<String> interfaces = new ArrayList<>();
            for (int i = 0; i < interfaceCount; i++) {
                interfaces.add(utf8.get(classes.get(in.readUnsignedShort())));
            }
            return new ClassFileInfo(name, superName, List.copyOf(interfaces), List.copyOf(utf8.values()));
        } catch (IOException | NullPointerException e) {
            throw new IllegalArgumentException("Cannot read class file: " + e.getMessage(), e);
        }
    }
}
