package ffm.ffi.api;

import java.lang.foreign.*;
import java.lang.foreign.ValueLayout.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

        default Field<L> asField(String name) {
            return new Field<>(name, this);
        }
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

        record pointer<T extends MemoryLayout>(AddressLayout layout, T target, long size, Class<?> binding) implements
                                                                                                            Primitive<AddressLayout> {
            public static <L extends MemoryLayout> pointer<SequenceLayout> array(L v, long size) {
                var t = MemoryLayout.sequenceLayout(size, v);
                return new pointer<>(AddressLayout.ADDRESS.withTargetLayout(t), t, size, Void.class);
            }

            public pointer(T target) {

                this(
                        AddressLayout.ADDRESS.withTargetLayout(target),
                        target,
                        1, Void.class);
            }

            public <L extends MemoryLayout> pointer<L> target(L v) {
                return new pointer<>(layout.withTargetLayout(v), v, 1, binding);
            }

            public <L extends MemoryLayout> pointer<SequenceLayout> target(L v, long size) {
                var t = MemoryLayout.sequenceLayout(size, v);
                return new pointer<>(layout.withTargetLayout(t), t, size, binding);
            }

        }
    }

    sealed interface Pointer extends Type<AddressLayout> {
        MemoryLayout target();

        sealed abstract class Base<ML extends MemoryLayout> implements Pointer {
            public final AddressLayout layout;
            public final ML target;

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof Base<?> base)) return false;
                return Objects.equals(layout, base.layout) && Objects.equals(target, base.target);
            }

            @Override
            public int hashCode() {
                return Objects.hash(layout, target);
            }

            public ML target() {
                return target;
            }

            public AddressLayout layout() {
                return layout;
            }

            protected Base(ML target) {
                this.layout = ValueLayout.ADDRESS.withTargetLayout(target);
                this.target = target;
            }

            protected MemorySegment value(MemorySegment ms) {
                return ms.get(layout, 0);
            }
        }

        static <T> Struct<T> of(long byteSize, Reader<T> reader, Writer<T> writer) {
            return new Struct<>(byteSize, reader, writer);
        }

        final class Struct<T> extends Base<MemoryLayout> {
            final Reader<T> reader;
            final Writer<T> writer;

            public Struct(long byteSize, Reader<T> reader, Writer<T> writer) {
                super(MemoryLayout.sequenceLayout(byteSize, ValueLayout.JAVA_BYTE));
                this.reader = reader;
                this.writer = writer;
            }

            public T get(MemorySegment ms) {
                return reader.apply(ms);
            }

            public MemorySegment set(MemorySegment ms, T value) {
                return writer.apply(ms, value);
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof Struct<?> struct)) return false;
                if (!super.equals(o)) return false;
                return Objects.equals(reader, struct.reader) && Objects.equals(writer, struct.writer);
            }

            @Override
            public int hashCode() {
                return Objects.hash(super.hashCode(), reader, writer);
            }
        }

        Int8 Int8 = new Int8();

        final class Int8 extends Base<ValueLayout.OfByte> {
            public Int8() {
                super(ValueLayout.JAVA_BYTE);
            }

            public byte get(MemorySegment m) {
                return value(m).get(target, 0);
            }

            public MemorySegment set(MemorySegment m, byte v) {
                value(m).set(target, 0, v);
                return m;
            }

            public MemorySegment set(MemorySegment m, byte[] v) {
                var ms = value(m).reinterpret(v.length);
                ms.copyFrom(MemorySegment.ofArray(v));
                return m;
            }

            public byte[] get(MemorySegment m, long size) {
                return value(m).reinterpret(size).toArray(target);
            }

        }

        Int16 Int16 = new Int16();

        final class Int16 extends Base<ValueLayout.OfShort> {
            public Int16() {
                super(ValueLayout.JAVA_SHORT);
            }

            public short get(MemorySegment m) {
                return value(m).get(target, 0);
            }

            public MemorySegment set(MemorySegment m, short v) {
                value(m).set(target, 0, v);
                return m;
            }

            public MemorySegment set(MemorySegment m, short[] v) {
                var ms = value(m).reinterpret(v.length);
                ms.copyFrom(MemorySegment.ofArray(v));
                return m;
            }

            public short[] get(MemorySegment m, long size) {
                return value(m).reinterpret(size).toArray(target);
            }
        }

        Int32 Int32 = new Int32();

        final class Int32 extends Base<ValueLayout.OfInt> {
            public Int32() {
                super(ValueLayout.JAVA_INT);
            }

            public int get(MemorySegment m) {
                return value(m).get(target, 0);
            }

            public MemorySegment set(MemorySegment m, int v) {
                value(m).set(target, 0, v);
                return m;
            }

            public MemorySegment set(MemorySegment m, int[] v) {
                var ms = value(m).reinterpret(v.length);
                ms.copyFrom(MemorySegment.ofArray(v));
                return m;
            }

            public int[] get(MemorySegment m, long size) {
                return value(m).reinterpret(size).toArray(target);
            }
        }

        Int64 Int64 = new Int64();

        final class Int64 extends Base<ValueLayout.OfLong> {
            public Int64() {
                super(ValueLayout.JAVA_LONG);
            }

            public long get(MemorySegment m) {
                return value(m).get(target, 0);
            }

            public MemorySegment set(MemorySegment m, long v) {
                value(m).set(target, 0, v);
                return m;
            }

            public MemorySegment set(MemorySegment m, long[] v) {
                var ms = value(m).reinterpret(v.length);
                ms.copyFrom(MemorySegment.ofArray(v));
                return m;
            }

            public long[] get(MemorySegment m, long size) {
                return value(m).reinterpret(size).toArray(target);
            }
        }

        Float32 Float32 = new Float32();

        final class Float32 extends Base<ValueLayout.OfFloat> {
            public Float32() {
                super(ValueLayout.JAVA_FLOAT);
            }

            public float get(MemorySegment m) {
                return value(m).get(target, 0);
            }

            public MemorySegment set(MemorySegment m, float v) {
                value(m).set(target, 0, v);
                return m;
            }

            public MemorySegment set(MemorySegment m, float[] v) {
                var ms = value(m).reinterpret(v.length);
                ms.copyFrom(MemorySegment.ofArray(v));
                return m;
            }

            public float[] get(MemorySegment m, long size) {
                return value(m).reinterpret(size).toArray(target);
            }
        }

        Float64 Float64 = new Float64();

        final class Float64 extends Base<ValueLayout.OfDouble> {
            public Float64() {
                super(ValueLayout.JAVA_DOUBLE);
            }

            public double get(MemorySegment m) {
                return value(m).get(target, 0);
            }

            public MemorySegment set(MemorySegment m, double v) {
                value(m).set(target, 0, v);
                return m;
            }

            public MemorySegment set(MemorySegment m, double[] v) {
                var ms = value(m).reinterpret(v.length);
                ms.copyFrom(MemorySegment.ofArray(v));
                return m;
            }

            public double[] get(MemorySegment m, long size) {
                return value(m).reinterpret(size).toArray(target);
            }
        }

    }

    MemorySegment NULL = MemorySegment.NULL;
    //region CType
    Primitive<OfBoolean> C_BOOL = FFI.BOOL;
    Primitive<OfByte> C_BYTE = FFI.INT8;
    Primitive<OfByte> C_U_BYTE = FFI.INT8;
    Primitive<OfByte> C_CHAR = FFI.INT8;
    Primitive<OfByte> C_U_CHAR = FFI.INT8;
    Primitive<OfShort> C_SHORT = FFI.INT16;
    Primitive<OfShort> C_U_SHORT = FFI.INT16;
    Primitive<OfInt> C_INT = FFI.INT32;
    Primitive<OfInt> C_LONG = FFI.INT32;
    Primitive<OfInt> C_U_LONG = FFI.INT32;
    Primitive<OfInt> C_U_INT = FFI.INT32;
    Primitive<OfLong> C_LONG_LONG = FFI.INT64;
    Primitive<OfLong> C_U_LONG_LONG = FFI.INT64;
    Primitive<OfFloat> C_FLOAT = FFI.FLOAT32;
    Primitive<OfDouble> C_DOUBLE = FFI.FLOAT64;
    Primitive<OfDouble> C_LONG_DOUBLE = FFI.FLOAT64;
    Primitive<OfLong> C_SIZE_T = FFI.INT64;
    Primitive.pointer<SequenceLayout> C_POINTER = FFI.POINTER;
    Primitive<AddressLayout> C_STRING = FFI.STRING;
    //endregion
    //region common type
    Primitive<OfBoolean> BOOL = new Primitive.entity<>(ValueLayout.JAVA_BOOLEAN, boolean.class);
    Primitive<OfByte> INT8 = new Primitive.entity<>(ValueLayout.JAVA_BYTE, byte.class);
    Primitive<OfShort> INT16 = new Primitive.entity<>(ValueLayout.JAVA_SHORT, short.class);
    Primitive<OfInt> INT32 = new Primitive.entity<>(ValueLayout.JAVA_INT, int.class);
    Primitive<OfLong> INT64 = new Primitive.entity<>(ValueLayout.JAVA_LONG, long.class);
    Primitive<OfFloat> FLOAT32 = new Primitive.entity<>(ValueLayout.JAVA_FLOAT, float.class);
    Primitive<OfDouble> FLOAT64 = new Primitive.entity<>(ValueLayout.JAVA_DOUBLE, double.class);
    Primitive<OfChar> RUNE = new Primitive.entity<>(ValueLayout.JAVA_CHAR, char.class);
    Primitive<OfLong> SIZE = INT64;
    Primitive.pointer<SequenceLayout> POINTER = Primitive.pointer.array(ValueLayout.JAVA_BYTE, Long.MAX_VALUE);
    Primitive<AddressLayout> STRING = new Primitive.entity<>(
            ValueLayout.ADDRESS.withTargetLayout(
                    MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE)), String.class);
    //endregion

    /// array type
    sealed interface Array<L extends MemoryLayout, C extends Type<L>> extends Type<SequenceLayout> {

        C component();

        SequenceLayout layout(long size);

        default SequenceLayout layout() {
            return layout(Long.MAX_VALUE);
        }

        default Field<SequenceLayout> asField(String name) {
            return new Field<>(name, this);
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

        default String[] strings(MemorySegment ms, long size) {
            assert component() == STRING : "not a STRING";
            return ms.reinterpret(size).elements(this.layout(size)).map(x -> x.getString(0)).toArray(String[]::new);
        }


    }

    static <L extends MemoryLayout, C extends Type<L>> Array<L, C> array(C type) {
        return new Array.entity<>(type);
    }

    //region arrays
    //        Array<OfBoolean, Primitive<OfBoolean>> BOOL_ARRAY = new entity<>(Primitive.BOOL);
    Array<OfByte, Primitive<OfByte>> INT8_ARRAY = new Array.entity<>(INT8);
    Array<OfShort, Primitive<OfShort>> INT16_ARRAY = new Array.entity<>(INT16);
    Array<OfInt, Primitive<OfInt>> INT32_ARRAY = new Array.entity<>(INT32);
    Array<OfLong, Primitive<OfLong>> INT64_ARRAY = new Array.entity<>(INT64);
    Array<OfFloat, Primitive<OfFloat>> FLOAT32_ARRAY = new Array.entity<>(FLOAT32);
    Array<OfDouble, Primitive<OfDouble>> FLOAT64_ARRAY = new Array.entity<>(FLOAT64);
    Array<OfChar, Primitive<OfChar>> RUNE_ARRAY = new Array.entity<>(RUNE);
    Array<AddressLayout, Primitive<AddressLayout>> POINTER_ARRAY = new Array.entity<>(POINTER);
    Array<AddressLayout, Primitive<AddressLayout>> STRING_ARRAY = new Array.entity<>(STRING);
    //endregion

    /// field hold a field name and type, if type is padding,name should be null
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

    /// padding type
    sealed interface Padding extends Type<PaddingLayout> {
        long bytes();

        default Field<PaddingLayout> asField() {
            return new Field<>(null, this);
        }

        @Override
        default Field<PaddingLayout> asField(String name) {
            return new Field<>(null, this);
        }

        record entity(long bytes) implements Padding {

            @Override
            public PaddingLayout layout() {
                return MemoryLayout.paddingLayout(bytes);
            }
        }

    }

    /// change memory layout align
    static MemoryLayout align(MemoryLayout layout, long align) {
        return switch (layout) {
            case PaddingLayout p -> p;
            case ValueLayout v -> v.withByteAlignment(align);
            case GroupLayout g -> {
                var alignedMembers = g.memberLayouts()
                                      .stream()
                                      .map(m -> align(m, align))
                                      .toArray(MemoryLayout[]::new);
                yield g instanceof StructLayout ?
                        MemoryLayout.structLayout(alignedMembers)
                        : MemoryLayout.unionLayout(alignedMembers);
            }
            case SequenceLayout s -> MemoryLayout.sequenceLayout(s.elementCount(), align(s.elementLayout(), align));
        };
    }

    /// create a padding with size
    static Padding padding(long bytes) {
        return new Padding.entity(bytes);
    }

    sealed interface Structural<T> extends Type<GroupLayout> {
        @Override
        GroupLayout layout();

        /// accessor for each field.
        Map<String, VarHandle> accessor();

        sealed interface Struct<T> extends Structural<T> {
            /// @param ms the segment to read from
            /// @return value
            T read(MemorySegment ms);

            /// @param ms    the segment to write to
            /// @param value the value
            /// @return ms
            MemorySegment write(MemorySegment ms, T value);
        }

        sealed interface WriteOnly<T> extends Structural<T> {
            /// @param ms    the segment to write to
            /// @param value the value
            /// @return ms
            MemorySegment write(MemorySegment ms, T value);
        }

        sealed interface ReadOnly<T> extends Structural<T> {
            /// @param ms the segment to read from
            /// @return value
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

            @Override
            public Map<String, VarHandle> accessor() {
                return Arrays.stream(fields).sequential()
                             .filter(x -> x.name != null && !x.name.isEmpty())
                             .collect(Collectors.toMap(v -> v.name, v -> v.layout().varHandle(
                                     MemoryLayout.PathElement.groupElement(v.name))));
            }
        }

        Field<?>[] fields();
    }

    /// create read-write binding struct
    static <T> Structural.Struct<T> struct(Reader<T> reader, Writer<T> writer, Field<?>... fields) {
        return new Structural.entity<>(fields, reader, writer);
    }

    /// create  read-only binding struct
    static <T> Structural.ReadOnly<T> struct(Reader<T> reader, Field<?>... fields) {
        return new Structural.entity<>(fields, reader, null);
    }

    /// create a write-only binding struct
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

    /// create a Union type
    static Union union(Field<?>... fields) {
        return new Union.entity(fields);
    }

    /// a Foreign Function or a Native (JVM) function stub.
    /// variadic FF should use {@link  #linkVarArgs(Linker, SymbolLookup, String, Type[])}.
    sealed interface Delegate extends FFI {
        /// The descriptor
        FunctionDescriptor descriptor();

        /// build  instance method stub
        default MemorySegment stub(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                   Object value,
                                   String name,
                                   MethodType methodType
                                  ) throws NoSuchMethodException, IllegalAccessException {
            var mh = lookup.bind(value, name, methodType);
            return linker.upcallStub(mh, descriptor(), arena);
        }

        /// build  instance method stub
        ///
        /// @see #stub(Arena, Linker, MethodHandles.Lookup, Object, String, MethodType)
        default MemorySegment stub(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                   Object value,
                                   String name,
                                   Class<?> returns,
                                   Class<?>... arguments) throws NoSuchMethodException, IllegalAccessException {
            return stub(arena, linker, lookup, value, name, MethodType.methodType(returns, arguments));
        }

        /// build static method stub
        default MemorySegment stubStatic(Arena arena, Linker linker,
                                         MethodHandles.Lookup lookup,
                                         Class<?> holder, String name,
                                         MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
            var mh = lookup.findStatic(holder, name, methodType);
            return linker.upcallStub(mh, descriptor(), arena);
        }

        /// @see #stubStatic(Arena, Linker, MethodHandles.Lookup, Class, String, MethodType)
        default MemorySegment stubStatic(Arena arena, Linker linker,
                                         MethodHandles.Lookup lookup,
                                         Class<?> holder, String name,
                                         Class<?> returns, Class<?>... arguments) throws NoSuchMethodException,
                IllegalAccessException {
            return stubStatic(arena, linker, lookup, holder, name, MethodType.methodType(returns, arguments));
        }

        /// @see #stubConstructor(Arena, Linker, MethodHandles.Lookup, Class, MethodType)
        default MemorySegment stubConstructor(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                              Class<?> type, Class<?>... arguments) throws NoSuchMethodException,
                IllegalAccessException {
            return stubConstructor(arena, linker, lookup, type, MethodType.methodType(type, arguments));
        }

        /// build constructor stub
        default MemorySegment stubConstructor(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                              Class<?> holder, MethodType methodType) throws NoSuchMethodException,
                IllegalAccessException {
            var mh = lookup.findConstructor(holder, methodType);
            return linker.upcallStub(mh, descriptor(), arena);
        }

        /// link to foreign function
        default MethodHandle link(Linker linker, SymbolLookup lookup, String name) {
            var addr = lookup.findOrThrow(name);
            return linker.downcallHandle(addr, descriptor());
        }

        /// link to foreign function
        default MethodHandle linkVarArgs(Linker linker, SymbolLookup lookup, String name, Type<?>... variadic) {
            var base = descriptor();
            var desc = descriptor().appendArgumentLayouts(
                    Arrays.stream(variadic).map(Type::layout).toArray(MemoryLayout[]::new));
            var opt = Linker.Option.firstVariadicArg(base.argumentLayouts().size());
            var addr = lookup.findOrThrow(name);
            return linker.downcallHandle(addr, desc, opt).asSpreader(Object[].class, variadic.length);
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

        /// @return function return type may null for void returns
        Type<?> returns();

        /// @return function arguments, empty for no arguments. variadic arguments check {@link #linkVarArgs(Linker, SymbolLookup, String, Type[])}
        Type<?>[] arguments();
    }

    /// create a {@link Delegate}
    static Delegate delegate(Type<?> retType, Type<?>... argument) {
        return new Delegate.entity(retType, argument);
    }

    /// create a {@link Delegate} that return nothing
    static Delegate delegateVoid(Type<?>... argument) {
        return new Delegate.entity(null, argument);
    }

    /// @param ms a memory hold value `*Value`
    /// @return an address hold the memory location, `**Value`
    static MemorySegment reference(SegmentAllocator arena, MemorySegment ms) {
        return arena.allocateFrom(ValueLayout.ADDRESS, ms);
    }

    /// @param ms an address hold the memory location, `**Value`
    /// @return an memory hold value `*Value`
    static MemorySegment dereference(MemorySegment ms) {
        return ms.get(ValueLayout.ADDRESS, 0);
    }

    /// referenced memory
    sealed interface Reference {
        sealed interface RefStruct<T> extends Reference {
            T get();

            RefStruct<T> set(T val);

            record entity<T>(MemorySegment memory, Structural.Struct<T> struct) implements RefStruct<T> {
                @Override
                public MemoryLayout valueLayout() {
                    return struct.layout();
                }

                @Override
                public T get() {
                    return struct.read(memory);
                }

                @Override
                public RefStruct<T> set(T val) {
                    struct.write(memory, val);
                    return this;
                }
            }
        }

        static <T> RefStruct<T> struct(Arena arena, Structural.Struct<T> type) {
            return new RefStruct.entity<>(arena.allocate(type.layout().byteSize()), type);
        }

        MemoryLayout valueLayout();

        default MemoryLayout layout() {
            return POINTER.layout().withTargetLayout(valueLayout());
        }

        /// the memory that holding the value
        MemorySegment memory();

        static Int8 int8(Arena arena) {
            return new Int8.entity(arena.allocate(1));
        }

        sealed interface Int8 extends Reference {
            @Override
            default MemoryLayout valueLayout() {
                return ValueLayout.JAVA_BYTE;
            }

            default byte value() {
                return memory().get(ValueLayout.JAVA_BYTE, 0);
            }

            default Int8 value(byte value) {
                var addr = memory();
                addr.set(ValueLayout.JAVA_BYTE, 0, value);
                return this;
            }

            record entity(MemorySegment memory) implements Int8 {}
        }

        static Int16 int16(Arena arena) {
            return new Int16.entity(arena.allocate(2));
        }

        sealed interface Int16 extends Reference {
            @Override
            default MemoryLayout valueLayout() {
                return ValueLayout.JAVA_SHORT;
            }

            default short value() {
                return memory().get(ValueLayout.JAVA_SHORT, 0);
            }

            default Int16 value(short value) {
                var addr = memory();
                addr.set(ValueLayout.JAVA_SHORT, 0, value);
                return this;
            }

            record entity(MemorySegment memory) implements Int16 {}
        }

        static Int32 int32(Arena arena) {
            return new Int32.entity(arena.allocate(4));
        }

        sealed interface Int32 extends Reference {
            @Override
            default MemoryLayout valueLayout() {
                return ValueLayout.JAVA_INT;
            }

            default int value() {
                return memory().get(ValueLayout.JAVA_INT, 0);
            }

            default Int32 value(int value) {
                var addr = memory();
                addr.set(ValueLayout.JAVA_INT, 0, value);
                return this;
            }


            record entity(MemorySegment memory) implements Int32 {}
        }

        static Int64 int64(Arena arena) {
            return new Int64.entity(arena.allocate(8));
        }

        sealed interface Int64 extends Reference {
            @Override
            default MemoryLayout valueLayout() {
                return ValueLayout.JAVA_LONG;
            }

            default long value() {
                return memory().get(ValueLayout.JAVA_LONG, 0);
            }

            default Int64 value(long value) {
                var addr = memory();
                addr.set(ValueLayout.JAVA_LONG, 0, value);
                return this;
            }


            record entity(MemorySegment memory) implements Int64 {}
        }

        static Float32 float32(Arena arena) {
            return new Float32.entity(arena.allocate(4));
        }

        sealed interface Float32 extends Reference {
            @Override
            default MemoryLayout valueLayout() {
                return ValueLayout.JAVA_FLOAT;
            }

            default float value() {
                return memory().get(ValueLayout.JAVA_FLOAT, 0);
            }

            default Float32 value(float value) {
                var addr = memory();
                addr.set(ValueLayout.JAVA_FLOAT, 0, value);
                return this;
            }


            record entity(MemorySegment memory) implements Float32 {}
        }

        static Float64 float64(Arena arena) {
            return new Float64.entity(arena.allocate(8));
        }

        sealed interface Float64 extends Reference {
            @Override
            default MemoryLayout valueLayout() {
                return ValueLayout.JAVA_DOUBLE;
            }

            default double value() {
                return memory().get(ValueLayout.JAVA_DOUBLE, 0);
            }

            default Float64 value(double value) {
                var addr = memory();
                addr.set(ValueLayout.JAVA_DOUBLE, 0, value);
                return this;
            }


            record entity(MemorySegment memory) implements Float64 {}
        }
    }

    ///  current OS type
    enum OS {
        DARWIN,
        FREEBSD,
        NETBSD,
        OPENBSD,
        DRAGONFLY,
        LINUX,
        SOLARIS,
        WINDOWS,
        AIX,
        IBMI,
        ZLINUX,
        MIDNIGHTBSD,
        UNKNOWN;

        public String toString() {
            return this.name().toLowerCase();
        }

        public static final OS CURRENT;

        static {
            CURRENT = determineOS();
        }

        static OS determineOS() {
            var osName = System.getProperty("os.name").split(" ")[0];
            if (!startsWithIgnoreCase(osName, "mac") && !startsWithIgnoreCase(osName, "darwin")) {
                if (startsWithIgnoreCase(osName, "linux")) {
                    return OS.LINUX;
                } else if (!startsWithIgnoreCase(osName, "sunos") && !startsWithIgnoreCase(osName, "solaris")) {
                    if (startsWithIgnoreCase(osName, "aix")) {
                        return OS.AIX;
                    } else if (!startsWithIgnoreCase(osName, "os400") && !startsWithIgnoreCase(osName, "os/400")) {
                        if (startsWithIgnoreCase(osName, "openbsd")) {
                            return OS.OPENBSD;
                        } else if (startsWithIgnoreCase(osName, "freebsd")) {
                            return OS.FREEBSD;
                        } else if (startsWithIgnoreCase(osName, "dragonfly")) {
                            return OS.DRAGONFLY;
                        } else if (startsWithIgnoreCase(osName, "windows")) {
                            return OS.WINDOWS;
                        } else {
                            return startsWithIgnoreCase(osName, "midnightbsd") ? OS.MIDNIGHTBSD : OS.UNKNOWN;
                        }
                    } else {
                        return OS.IBMI;
                    }
                } else {
                    return OS.SOLARIS;
                }
            } else {
                return OS.DARWIN;
            }
        }

        private static boolean startsWithIgnoreCase(String s1, String s2) {
            return s1.startsWith(s2) || s1.toUpperCase().startsWith(s2.toUpperCase()) || s1.toLowerCase()
                                                                                           .startsWith(
                                                                                                   s2.toLowerCase());
        }
    }

    ///  current OS CPU arch
    enum CPU {
        I386,
        X86_64,
        PPC,
        PPC64,
        PPC64LE,
        SPARC,
        SPARCV9,
        S390X,
        MIPS32,
        ARM,
        AARCH64,
        MIPS64EL,
        LOONGARCH64,
        RISCV64,
        UNKNOWN;

        public String toString() {
            return this.name().toLowerCase();
        }

        public static final CPU CURRENT;

        static {
            CURRENT = determineCPU();
        }

        static CPU determineCPU() {
            String archString = System.getProperty("os.arch");
            if (!"x86".equalsIgnoreCase(archString)
                    && !"i386".equalsIgnoreCase(archString)
                    && !"i86pc".equalsIgnoreCase(archString)
                    && !"i686".equalsIgnoreCase(archString)) {
                if (!"x86_64".equalsIgnoreCase(archString) && !"amd64".equalsIgnoreCase(archString)) {
                    if (!"ppc".equalsIgnoreCase(archString) && !"powerpc".equalsIgnoreCase(archString)) {
                        if (!"ppc64".equalsIgnoreCase(archString) && !"powerpc64".equalsIgnoreCase(archString)) {
                            if (!"ppc64le".equalsIgnoreCase(archString) && !"powerpc64le".equalsIgnoreCase(
                                    archString)) {
                                if (!"s390".equalsIgnoreCase(archString) && !"s390x".equalsIgnoreCase(archString)) {
                                    if ("aarch64".equalsIgnoreCase(archString)) {
                                        return CPU.AARCH64;
                                    } else if (!"arm".equalsIgnoreCase(archString) && !"armv7l".equalsIgnoreCase(
                                            archString)) {
                                        if (!"mips64".equalsIgnoreCase(archString) && !"mips64el".equalsIgnoreCase(
                                                archString)) {
                                            if ("loongarch64".equalsIgnoreCase(archString)) {
                                                return CPU.LOONGARCH64;
                                            } else if ("riscv64".equalsIgnoreCase(archString)) {
                                                return CPU.RISCV64;
                                            } else {
                                                for (CPU cpu : CPU.values()) {
                                                    if (cpu.name().equalsIgnoreCase(archString)) {
                                                        return cpu;
                                                    }
                                                }

                                                return CPU.UNKNOWN;
                                            }
                                        } else {
                                            return CPU.MIPS64EL;
                                        }
                                    } else {
                                        return CPU.ARM;
                                    }
                                } else {
                                    return CPU.S390X;
                                }
                            } else {
                                return CPU.PPC64LE;
                            }
                        } else {
                            return "little".equals(
                                    System.getProperty("sun.cpu.endian")) ? CPU.PPC64LE : CPU.PPC64;
                        }
                    } else {
                        return OS.IBMI.equals(OS.CURRENT) ? CPU.PPC64 : CPU.PPC;
                    }
                } else {
                    return CPU.X86_64;
                }
            } else {
                return CPU.I386;
            }
        }
    }


}
