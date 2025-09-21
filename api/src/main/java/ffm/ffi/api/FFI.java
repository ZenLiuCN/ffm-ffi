package ffm.ffi.api;

import lombok.SneakyThrows;

import java.lang.foreign.*;
import java.lang.foreign.ValueLayout.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Basic FFI via FFM helpers.
 *
 * @author Zen.Liu
 * @since 2025-09-21
 */
public interface FFI {
    record ObjectReferencer<T>(
            Map<MemorySegment, T> referenced,
            Map<T, MemorySegment> dereferenced,
            Arena arena) {
        public ObjectReferencer(Arena arena) {
            this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), arena);
        }

        public MemorySegment register(T value) {
            if (dereferenced.containsKey(value)) {
                return dereferenced.get(value);
            }
            var p = arena.allocate(ValueLayout.ADDRESS);
            referenced.put(p, value);
            dereferenced.put(value, p);
            return p;
        }

        public boolean invalidate(T value) {
            if (!dereferenced.containsKey(value)) {
                return false;
            }
            var p = dereferenced.remove(value);
            if (p != null) {
                referenced.remove(p);
                if (p.isLoaded()) p.unload();
            }
            return true;
        }

        public boolean invalidate(MemorySegment pointer) {
            if (!referenced.containsKey(pointer)) {
                return false;
            }
            var p = referenced.remove(pointer);
            if (p != null) dereferenced.remove(p);
            if (pointer.isLoaded()) pointer.unload();
            return true;
        }
    }

    static <T> ObjectReferencer<T> referencer(Arena arena) {
        return new ObjectReferencer<>(arena);
    }

    interface Type<L extends MemoryLayout> extends FFI {
        /// memory layout of type, for delegate this method always throws.
        L layout();
    }

    interface Reader<T> {
        T apply(MemorySegment ms);
    }

    interface Writer<T> {
        MemorySegment apply(MemorySegment ms, T value);
    }

    sealed interface Primitive<L extends MemoryLayout> extends Type<L> {
        Class<?> binding();

        default boolean bool(MemorySegment ms) {
            assert binding() == boolean.class : "not a boolean";
            return ms.get((OfBoolean) layout(), 0);
        }

        default byte int8(MemorySegment ms) {
            assert binding() == byte.class : "not a int8";
            return ms.get((OfByte) layout(), 0);
        }

        default short int16(MemorySegment ms) {
            assert binding() == short.class : "not a int16";
            return ms.get((OfShort) layout(), 0);
        }

        default int int32(MemorySegment ms) {
            assert binding() == int.class : "not a int32";
            return ms.get((OfInt) layout(), 0);
        }

        default long int64(MemorySegment ms) {
            assert binding() == long.class : "not a int64";
            return ms.get((OfLong) layout(), 0);
        }

        default float float32(MemorySegment ms) {
            assert binding() == float.class : "not a float32";
            return ms.get((OfLong) layout(), 0);
        }

        default double float64(MemorySegment ms) {
            assert binding() == double.class : "not a float64";
            return ms.get((OfLong) layout(), 0);
        }

        default char rune(MemorySegment ms) {
            assert binding() == char.class : "not a rune";
            return ms.get((OfChar) layout(), 0);
        }

        default ByteBuffer buffer(MemorySegment ms, long size) {
            assert binding() == void.class : "not a pointer";
            return ms.reinterpret(size).asByteBuffer();
        }

        default String cstring(MemorySegment ms) {
            assert binding() == String.class : "not a CSTRING";
            return ms.getString(0);
        }

        record entity<L extends MemoryLayout>(L layout, Class<?> binding) implements Primitive<L> {

        }
    }

    Primitive<OfBoolean> BOOL = new Primitive.entity<>(ValueLayout.JAVA_BOOLEAN, boolean.class);
    Primitive<OfByte> INT8 = new Primitive.entity<>(ValueLayout.JAVA_BYTE, byte.class);
    Primitive<OfShort> INT16 = new Primitive.entity<>(ValueLayout.JAVA_SHORT, short.class);
    Primitive<OfInt> INT32 = new Primitive.entity<>(ValueLayout.JAVA_INT, int.class);
    Primitive<OfLong> INT64 = new Primitive.entity<>(ValueLayout.JAVA_LONG, long.class);
    Primitive<OfFloat> FLOAT32 = new Primitive.entity<>(ValueLayout.JAVA_FLOAT, float.class);
    Primitive<OfDouble> FLOAT64 = new Primitive.entity<>(ValueLayout.JAVA_DOUBLE, double.class);
    Primitive<OfChar> RUNE = new Primitive.entity<>(ValueLayout.JAVA_CHAR, char.class);
    Primitive<OfLong> SIZE = INT64;
    Primitive<AddressLayout> POINTER = new Primitive.entity<>(
            ValueLayout.ADDRESS.withTargetLayout(
                    MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE)), void.class);
    Primitive<AddressLayout> CSTRING = new Primitive.entity<>(
            ValueLayout.ADDRESS.withTargetLayout(
                    MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE)), String.class);

    sealed interface Array<L extends MemoryLayout, C extends Type<L>> extends Type<SequenceLayout> {

        C component();

        SequenceLayout layout(long size);

        default SequenceLayout layout() {
            return layout(Long.MAX_VALUE);
        }

        record entity<L extends MemoryLayout, C extends Type<L>>(C component) implements Array<L, C> {
            @Override
            public SequenceLayout layout(long size) {
                return MemoryLayout.sequenceLayout(size, component.layout());
            }
        }

//
//        default boolean[] bool(MemorySegment ms,long size) {
//            assert component() == Primitive.BOOL : "not a boolean";
//            return ms.reinterpret(size).toArray(Primitive.BOOL.layout());
//        }

        default byte[] int8(MemorySegment ms, long size) {
            assert component() == INT8 : "not a INT8";
            return ms.reinterpret(size).toArray(INT8.layout());
        }

        default short[] int16(MemorySegment ms, long size) {
            assert component() == INT16 : "not a INT16";
            return ms.reinterpret(size).toArray(INT16.layout());
        }

        default int[] int32(MemorySegment ms, long size) {
            assert component() == INT32 : "not a INT32";
            return ms.reinterpret(size).toArray(INT32.layout());
        }

        default long[] int64(MemorySegment ms, long size) {
            assert component() == INT64 : "not a INT64";
            return ms.reinterpret(size).toArray(INT64.layout());
        }

        default float[] float32(MemorySegment ms, long size) {
            assert component() == FLOAT32 : "not a FLOAT32";
            return ms.reinterpret(size).toArray(FLOAT32.layout());
        }

        default double[] float64(MemorySegment ms, long size) {
            assert component() == FLOAT64 : "not a FLOAT64";
            return ms.reinterpret(size).toArray(FLOAT64.layout());
        }

        default char[] rune(MemorySegment ms, long size) {
            assert component() == RUNE : "not a RUNE";
            return ms.reinterpret(size).toArray(RUNE.layout());
        }

        default String[] cstrings(MemorySegment ms, long size) {
            assert component() == CSTRING : "not a CSTRING";
            return ms.reinterpret(size).elements(this.layout(size)).map(x -> x.getString(0)).toArray(String[]::new);
        }


    }

    static <L extends MemoryLayout, C extends Type<L>> Array<L, C> array(C type) {
        return new Array.entity<>(type);
    }

    //        Array<OfBoolean, Primitive<OfBoolean>> BOOL_ARRAY = new entity<>(Primitive.BOOL);
    Array<OfByte, Primitive<OfByte>> INT8_ARRAY = new Array.entity<>(INT8);
    Array<OfShort, Primitive<OfShort>> INT16_ARRAY = new Array.entity<>(INT16);
    Array<OfInt, Primitive<OfInt>> INT32_ARRAY = new Array.entity<>(INT32);
    Array<OfLong, Primitive<OfLong>> INT64_ARRAY = new Array.entity<>(INT64);
    Array<OfFloat, Primitive<OfFloat>> FLOAT32_ARRAY = new Array.entity<>(FLOAT32);
    Array<OfDouble, Primitive<OfDouble>> FLOAT64_ARRAY = new Array.entity<>(FLOAT64);
    Array<OfChar, Primitive<OfChar>> RUNE_ARRAY = new Array.entity<>(RUNE);
    Array<AddressLayout, Primitive<AddressLayout>> POINTER_ARRAY = new Array.entity<>(POINTER);
    Array<AddressLayout, Primitive<AddressLayout>> CSTRING_ARRAY = new Array.entity<>(CSTRING);

    record Field<L extends MemoryLayout>(String name, Type<L> type) {
        public MemoryLayout layout() {
            return name == null || name.isEmpty()
                    ? type.layout()
                    : type.layout().withName(name);
        }

        public Field<L> name(String name) {
            return new Field<>(name, type);
        }
    }

    sealed interface Padding extends Type<PaddingLayout> {
        long bytes();

        record entity(long bytes) implements Padding {

            @Override
            public PaddingLayout layout() {
                return MemoryLayout.paddingLayout(bytes);
            }
        }
    }

    sealed interface Structural<T> extends Type<GroupLayout> {
        @Override
        GroupLayout layout();

        sealed interface Struct<T> extends Structural<T> {
            T read(MemorySegment ms);

            MemorySegment write(MemorySegment ms, T value);
        }

        sealed interface WriteOnly<T> extends Structural<T> {
            MemorySegment write(MemorySegment ms, T value);
        }

        sealed interface ReadOnly<T> extends Structural<T> {
            T read(MemorySegment ms);
        }


        record entity<T>(Field<?>[] fields, Reader<T> reader, Writer<T> writer) implements
                                                                                Struct<T>,
                                                                                ReadOnly<T>,
                                                                                WriteOnly<T> {
            @Override
            public GroupLayout layout() {
                return MemoryLayout
                        .unionLayout(Arrays.stream(fields)
                                           .map(Field::layout)
                                           .toArray(MemoryLayout[]::new));
            }

            @Override
            public T read(MemorySegment ms) {
                assert reader != null : "have no reader";
                return reader.apply(ms);
            }

            @Override
            public MemorySegment write(MemorySegment ms, T value) {
                assert writer != null : "have no writer";
                return writer.apply(ms, value);
            }
        }

        Field<?>[] fields();
    }

    /// a read-write binding struct
    static <T> Structural.Struct<T> struct(Reader<T> reader, Writer<T> writer, Field<?>... fields) {
        return new Structural.entity<>(fields, reader, writer);
    }

    /// a read-only binding struct
    static <T> Structural.ReadOnly<T> struct(Reader<T> reader, Field<?>... fields) {
        return new Structural.entity<>(fields, reader, null);
    }

    /// a write-only binding struct
    static <T> Structural.WriteOnly<T> struct(Writer<T> writer, Field<?>... fields) {
        return new Structural.entity<>(fields, null, writer);
    }

    sealed interface Union extends Type<UnionLayout> {
        record entity(Field<?>[] options) implements Union {
            public entity {
                assert options.length > 1 : "must have at least two options";
                var o = options[0].layout().byteSize();
                assert Arrays.stream(options)
                             .allMatch(x -> x.layout().byteSize() == o) : "not all options have same byte size";
            }

            @Override
            public UnionLayout layout() {
                return MemoryLayout.unionLayout(Arrays.stream(options)
                                                      .map(Field::layout)
                                                      .toArray(MemoryLayout[]::new));
            }
        }

        Field<?>[] options();
    }

    static Union union(Field<?>... fields) {
        return new Union.entity(fields);
    }

    sealed interface Delegate extends FFI {

        FunctionDescriptor descriptor();

        @SneakyThrows
        default MemorySegment bind(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                   Object value,
                                   String name,
                                   MethodType methodType
                                   ) {
            var mh = lookup.bind(value, name, methodType);
            return linker.upcallStub(mh, descriptor(), arena);
        }
        @SneakyThrows
        default MemorySegment bind(Arena arena, Linker linker,MethodHandles.Lookup lookup,  Object value, String name, Class<?> returns,
                                   Class<?>... arguments) {
            return bind(arena,linker,lookup,value,name,MethodType.methodType(returns, arguments));
        }
        @SneakyThrows
        default MemorySegment bindStatic(Arena arena, Linker linker,
                                         MethodHandles.Lookup lookup,
                                         Class<?> holder, String name,
                                         MethodType methodType) {
            var mh = lookup.findStatic(holder, name, methodType);
            return linker.upcallStub(mh, descriptor(), arena);
        }

        @SneakyThrows
        default MemorySegment bindStatic(Arena arena, Linker linker,
                                         MethodHandles.Lookup lookup,
                                         Class<?> holder, String name,
                                         Class<?> returns, Class<?>... arguments) {
            return bindStatic(arena, linker, lookup, holder, name, MethodType.methodType(returns, arguments));
        }

        @SneakyThrows
        default MemorySegment bindConstructor(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                              Class<?> type,Class<?>... arguments) {
            return bindConstructor(arena,linker,lookup, type,MethodType.methodType(type, arguments));
        }
        @SneakyThrows
        default MemorySegment bindConstructor(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                              Class<?> holder,MethodType methodType) {
            var mh = lookup.findConstructor(holder,methodType);
            return linker.upcallStub(mh, descriptor(), arena);
        }

        @SneakyThrows
        default MethodHandle lookup(Linker linker, SymbolLookup lookup, String name) {
            var addr = lookup.findOrThrow(name);
            return linker.downcallHandle(addr, descriptor());
        }

        record entity(Type<?> returns, Type<?>[] arguments) implements Delegate {
            @Override
            public FunctionDescriptor descriptor() {
                return returns == null
                        ? FunctionDescriptor.ofVoid(
                        Arrays.stream(arguments).sequential().map(Type::layout).toArray(MemoryLayout[]::new))
                        : FunctionDescriptor.of(returns.layout(),
                                                Arrays.stream(arguments).sequential().map(Type::layout)
                                                      .toArray(MemoryLayout[]::new));
            }
        }

        Type<?> returns();

        Type<?>[] arguments();
    }

    static Delegate delegate(Type<?> retType, Type<?>... argument) {
        return new Delegate.entity(retType, argument);
    }

    static Delegate delegateVoid(Type<?>... argument) {
        return new Delegate.entity(null, argument);
    }
}
