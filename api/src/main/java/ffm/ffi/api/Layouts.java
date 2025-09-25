package ffm.ffi.api;

import java.lang.foreign.*;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * Builder classes for MemoryLayouts
 *
 * @author Zen.Liu
 * @since 2025-09-24
 */
public interface Layouts {
    ValueLayout.OfByte BYTE = ValueLayout.JAVA_BYTE;
    ValueLayout.OfShort SHORT = ValueLayout.JAVA_SHORT;
    ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG;
    ValueLayout.OfChar CHARACTER = ValueLayout.JAVA_CHAR;
    ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT;
    ValueLayout.OfDouble DOUBLE = ValueLayout.JAVA_DOUBLE;
    ValueLayout.OfBoolean BOOLEAN = ValueLayout.JAVA_BOOLEAN;
    AddressLayout Address = ValueLayout.ADDRESS;


    MemorySegment NULL = MemorySegment.NULL;

    interface LayoutFunctor<T extends MemoryLayout> extends Function<Layouts, T> {


        default <OUT extends MemoryLayout, V> LayoutFunctor<OUT> combine(V v, BiFunction<T, V, OUT> act) {
            return a -> act.apply(apply(a), v);
        }

        default <OUT extends MemoryLayout, V extends LayoutFunctor<VL>, VL extends MemoryLayout> LayoutFunctor<OUT> combine(
                V v, BiFunction<T, VL, OUT> act) {
            return a -> act.apply(apply(a), v.apply(a));
        }
    }

    interface Int64LayoutFunctor<T extends MemoryLayout> extends BiFunction<Layouts, Long, T> {
        T apply(Layouts v, long v1);

        @Override
        default T apply(Layouts layouts, Long aLong) {
            return apply(layouts, aLong.longValue());
        }

        default LayoutFunctor<T> curry(long v1) {
            return a -> apply(a, v1);
        }
    }

    interface PathFunctor<V> extends Function<V, PathElement> {
    }

    interface Int64PathFunctor extends PathFunctor<Long>, LongFunction<PathElement> {
        PathElement apply(long index);

        @Override
        default PathElement apply(Long aLong) {
            return apply(aLong.longValue());
        }
    }

    interface RangePathFunctor {
        PathElement apply(long start, long step);
    }

    interface LayoutSet<IN extends MemoryLayout, V, OUT extends MemoryLayout>
            extends BiFunction<LayoutFunctor<IN>, V, LayoutFunctor<OUT>> {
        @Override
        LayoutFunctor<OUT> apply(LayoutFunctor<IN> inLayoutFunctor, V v);

    }


    LayoutFunctor<ValueLayout.OfByte> INT8 = Layouts::int8;
    LayoutFunctor<ValueLayout.OfShort> INT16 = Layouts::int16;
    LayoutFunctor<ValueLayout.OfInt> INT32 = Layouts::int32;
    LayoutFunctor<ValueLayout.OfLong> INT64 = Layouts::int64;
    LayoutFunctor<ValueLayout.OfChar> CHAR = Layouts::character;
    LayoutFunctor<ValueLayout.OfFloat> FLOAT32 = Layouts::float32;
    LayoutFunctor<ValueLayout.OfDouble> FLOAT64 = Layouts::float64;
    LayoutFunctor<ValueLayout.OfBoolean> BOOL = Layouts::bool;
    LayoutFunctor<AddressLayout> POINTER = Layouts::string;
    LayoutFunctor<AddressLayout> STRING = Layouts::string;
    LayoutSet<? extends MemoryLayout, String, MemoryLayout> NAMED = (fn, name) ->
            fn.combine(name, MemoryLayout::withName);
    LayoutSet<? extends AddressLayout, LayoutFunctor<? extends MemoryLayout>, AddressLayout> TARGET =
            (fn, tar) -> fn.combine(tar, AddressLayout::withTargetLayout);

    PathFunctor<String> FIELD_NAME = PathElement::groupElement;
    Int64PathFunctor FIELD_INDEX = PathElement::groupElement;
    Int64PathFunctor ARRAY_INDEX = PathElement::sequenceElement;
    RangePathFunctor ARRAY_RANGE = PathElement::sequenceElement;
    Supplier<PathElement> DEREFERENCE = PathElement::dereferenceElement;
    Int64LayoutFunctor<PaddingLayout> PADDING = Layouts::padding;

    //region layout
    default PaddingLayout padding(long byteSize) {
        return MemoryLayout.paddingLayout(byteSize);
    }

    default SequenceLayout sequence(long count, MemoryLayout element) {
        return MemoryLayout.sequenceLayout(count, element);
    }

    default SequenceLayout sequence(long count, LayoutFunctor<? super MemoryLayout> element) {
        return MemoryLayout.sequenceLayout(count, element.apply(this));
    }

    default LayoutFunctor<SequenceLayout> sequenceFn(long count, LayoutFunctor<? super MemoryLayout> element) {
        return a -> MemoryLayout.sequenceLayout(count, element.apply(a));
    }

    default StructLayout struct(MemoryLayout... elements) {
        return MemoryLayout.structLayout(elements);
    }

    @SuppressWarnings("unchecked")
    default StructLayout struct(Function<Layouts, MemoryLayout>... elements) {
        return MemoryLayout.structLayout(
                Arrays.stream(elements).sequential().map(x -> x.apply(this)).toArray(MemoryLayout[]::new));
    }

    @SuppressWarnings("unchecked")
    default LayoutFunctor<StructLayout> structFn(LayoutFunctor<? super MemoryLayout>... elements) {
        return a -> MemoryLayout.structLayout(
                Arrays.stream(elements).sequential().map(x -> x.apply(a)).toArray(MemoryLayout[]::new));
    }

    default UnionLayout union(MemoryLayout... elements) {
        return MemoryLayout.unionLayout(elements);
    }

    @SuppressWarnings("unchecked")
    default UnionLayout union(LayoutFunctor<? super MemoryLayout>... elements) {
        return MemoryLayout.unionLayout(
                Arrays.stream(elements).sequential().map(x -> x.apply(this)).toArray(MemoryLayout[]::new)
                                       );
    }

    @SuppressWarnings("unchecked")
    default LayoutFunctor<UnionLayout> unionFn(LayoutFunctor<? super MemoryLayout>... elements) {
        return a -> MemoryLayout.unionLayout(
                Arrays.stream(elements).sequential().map(x -> x.apply(a)).toArray(MemoryLayout[]::new)
                                            );
    }

    //endregion
    //region values
    default ValueLayout.OfBoolean bool() {return ValueLayout.JAVA_BOOLEAN;}

    default ValueLayout.OfByte int8() {return ValueLayout.JAVA_BYTE;}

    default ValueLayout.OfShort int16() {return ValueLayout.JAVA_SHORT;}

    default ValueLayout.OfInt int32() {return ValueLayout.JAVA_INT;}

    default ValueLayout.OfLong int64() {return ValueLayout.JAVA_LONG;}

    default ValueLayout.OfChar character() {return ValueLayout.JAVA_CHAR;}

    default ValueLayout.OfFloat float32() {return ValueLayout.JAVA_FLOAT;}

    default ValueLayout.OfDouble float64() {return ValueLayout.JAVA_DOUBLE;}

    default AddressLayout reference() {return ValueLayout.ADDRESS;}

    default AddressLayout pointer() {
        return ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE));
    }

    default AddressLayout string() {
        return ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE));
    }

    //endregion
    //region groups
    default PathElement group(String name) {
        return PathElement.groupElement(name);
    }

    default PathElement group(long index) {
        return PathElement.groupElement(index);
    }


    default PathElement sequence(long index) {
        return PathElement.sequenceElement(index);
    }

    default PathElement range(long start, long step) {
        return PathElement.sequenceElement(start, step);
    }

    default PathElement dereference() {
        return PathElement.dereferenceElement();
    }

    //endregion
    //region helper
    default <L extends MemoryLayout> LayoutFunctor<MemoryLayout> name(String name, LayoutFunctor<L> src) {
        return a -> src.apply(a).withName(name);
    }

    default LayoutFunctor<MemoryLayout> aligned(long alignment,
                                                LayoutFunctor<? extends MemoryLayout> src) {
        return a -> src.apply(a).withByteAlignment(alignment);
    }

    default LayoutFunctor<? extends ValueLayout> order(ByteOrder order,
                                                       LayoutFunctor<? extends ValueLayout> src) {
        return a -> src.apply(a).withOrder(order);
    }

    default LayoutFunctor<? extends ValueLayout> unaligned(
            LayoutFunctor<? extends ValueLayout> src) {
        return a -> src.apply(a).withByteAlignment(1);
    }

    default LayoutFunctor<? extends AddressLayout> target(MemoryLayout target,
                                                          LayoutFunctor<? extends AddressLayout> src) {
        return a -> src.apply(a).withTargetLayout(target);
    }

    default LayoutFunctor<? extends AddressLayout> target(LayoutFunctor<? extends AddressLayout> target,
                                                          LayoutFunctor<? extends AddressLayout> src) {
        return a -> src.apply(a).withTargetLayout(target.apply(a));
    }

    /// reference to an un-boundaried bytes. same as `void *` in clang.
    default LayoutFunctor<? extends AddressLayout> any(LayoutFunctor<? extends AddressLayout> src) {
        return a -> src.apply(a).withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE));
    }

    //endregion
    class LayoutBuilder implements Layouts {

    }

    static LayoutBuilder layout() {
        return new LayoutBuilder();
    }

    class Func extends LayoutBuilder {
        private MemoryLayout returnType;
        private final List<MemoryLayout> argumentTypes = new ArrayList<>();

        Func() {}

        public Func returnType(LayoutFunctor<? extends MemoryLayout> layout) {
            this.returnType = layout.apply(this);
            return this;
        }

        public Func produce(LayoutFunctor<? extends MemoryLayout> layout) {
            this.returnType = layout.apply(this);
            return this;
        }

        @SafeVarargs
        public final Func apply(LayoutFunctor<? extends MemoryLayout>... layout) {
            for (var fn : layout) {
                this.argumentTypes.add(fn.apply(this));
            }
            return this;
        }

        public Func argument(LayoutFunctor<? extends MemoryLayout> layout) {
            this.argumentTypes.add(layout.apply(this));
            return this;
        }

        @SafeVarargs
        public final Func arguments(LayoutFunctor<? extends MemoryLayout>... layout) {
            for (var fn : layout) {
                this.argumentTypes.add(fn.apply(this));
            }
            return this;
        }

        public Func argumentAt(int index, LayoutFunctor<? extends MemoryLayout> layout) {
            this.argumentTypes.add(index, layout.apply(this));
            return this;
        }

        public FunctionDescriptor descriptor() {
            return returnType == null
                    ? FunctionDescriptor.ofVoid(argumentTypes.toArray(MemoryLayout[]::new))
                    : FunctionDescriptor.of(returnType, argumentTypes.toArray(MemoryLayout[]::new));
        }

        /// link to a function symbol by name
        public MethodHandle link(Linker linker, SymbolLookup symbol, String name,
                                 Linker.Option... options) {
            return linker.downcallHandle(symbol.findOrThrow(name), descriptor(), options);
        }

        /// create a signature for link, with first argument of the function symbol address.
        public MethodHandle link(Linker linker, Linker.Option... options) {
            return linker.downcallHandle(descriptor(), options);
        }

        /// *Note*: method handle not spreads
        public MethodHandle linkVariadic(Linker linker, SymbolLookup symbol, String name, MemoryLayout... variadic) {
            var base = descriptor();
            var desc = base.appendArgumentLayouts(variadic);
            var option = Linker.Option.firstVariadicArg(base.argumentLayouts().size());
            return linker.downcallHandle(symbol.findOrThrow(name), desc, option);
        }

        @SafeVarargs
        final public MethodHandle linkVariadic(Linker linker, SymbolLookup symbol, String name,
                                               LayoutFunctor<? extends MemoryLayout>... variadic) {

            return linkVariadic(linker, symbol, name, Arrays.stream(variadic).sequential().map(x -> x.apply(this))
                                                            .toArray(MemoryLayout[]::new));
        }

        /// *Note*: method handle not spreads
        public MethodHandle linkVariadic(Linker linker, MemoryLayout... variadic) {
            var base = descriptor();
            var desc = base.appendArgumentLayouts(variadic);
            var option = Linker.Option.firstVariadicArg(base.argumentLayouts().size());
            return linker.downcallHandle(desc, option);
        }

        @SafeVarargs
        final public MethodHandle linkVariadic(Linker linker, LayoutFunctor<? extends MemoryLayout>... variadic) {
            return linkVariadic(linker, Arrays.stream(variadic).sequential().map(x -> x.apply(this))
                                              .toArray(MemoryLayout[]::new));
        }

        /// *Note*: method handle are spreads with length of Object\[\]
        public MethodHandle linkVariadicSpreads(Linker linker, SymbolLookup symbol, String name,
                                                MemoryLayout... variadic) {
            var base = descriptor();
            var desc = base.appendArgumentLayouts(variadic);
            var option = Linker.Option.firstVariadicArg(base.argumentLayouts().size());
            return linker.downcallHandle(symbol.findOrThrow(name), desc, option)
                         .asSpreader(Object[].class, variadic.length);
        }

        @SafeVarargs
        final
        public MethodHandle linkVariadicSpreads(Linker linker, SymbolLookup symbol, String name,
                                                LayoutFunctor<? extends MemoryLayout>... variadic) {
            return linkVariadicSpreads(linker, symbol, name,
                                       Arrays.stream(variadic).sequential().map(x -> x.apply(this))
                                             .toArray(MemoryLayout[]::new));
        }

        /// *Note*: method handle are spreads with length of Object\[\]
        public MethodHandle linkVariadicSpreads(Linker linker, MemoryLayout... variadic) {
            var base = descriptor();
            var desc = base.appendArgumentLayouts(variadic);
            var option = Linker.Option.firstVariadicArg(base.argumentLayouts().size());
            return linker.downcallHandle(desc, option).asSpreader(Object[].class, variadic.length);
        }

        @SafeVarargs
        final
        public MethodHandle linkVariadicSpreads(Linker linker, LayoutFunctor<? extends MemoryLayout>... variadic) {

            return linkVariadicSpreads(linker, Arrays.stream(variadic).sequential().map(x -> x.apply(this))
                                                     .toArray(MemoryLayout[]::new));
        }

        /// create a stub, which is an address point to the java method instance.
        public MemorySegment stub(Arena arena, Linker linker, MethodHandle handle, Linker.Option... options) {
            return linker.upcallStub(handle, descriptor(), arena, options);
        }

        /// create a stub, which is an address point to the instance java method instance.
        public MemorySegment stub(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                  Object owner, String name, MethodType type,
                                  Linker.Option... options) {
            return sneaky((ThrowGet<? extends MemorySegment>) () -> linker.upcallStub(lookup.bind(owner, name, type),
                                                                                      descriptor(), arena, options));
        }

        public MemorySegment stub(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                  Object owner, String name, Class<?> retType,
                                  Class<?>... arguments) {
            return sneaky((ThrowGet<? extends MemorySegment>) () -> linker.upcallStub(
                    lookup.bind(owner, name, MethodType.methodType(retType, arguments)), descriptor(),
                    arena));
        }

        /// create a stub, which is an address point to the class static method.
        public MemorySegment stub(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                  Class<?> owner, String name, MethodType type,
                                  Linker.Option... options) {
            return sneaky(
                    (ThrowGet<? extends MemorySegment>) () -> linker.upcallStub(lookup.findStatic(owner, name, type),
                                                                                descriptor(), arena, options));
        }

        /// create a stub, which is an address point to the class static method.
        public MemorySegment stub(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                  Class<?> owner, String name, Class<?> retType,
                                  Class<?>... arguments) {
            return sneaky((ThrowGet<? extends MemorySegment>) () -> linker.upcallStub(
                    lookup.findStatic(owner, name, MethodType.methodType(retType, arguments)),
                    descriptor(), arena));
        }

        /// create a stub, which is an address point to the class constructor method.
        public MemorySegment stubConstructor(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                             Class<?> owner, MethodType type,
                                             Linker.Option... options) {
            return sneaky(
                    (ThrowGet<? extends MemorySegment>) () -> linker.upcallStub(lookup.findConstructor(owner, type),
                                                                                descriptor(), arena, options));
        }

        /// create a stub, which is an address point to the class constructor method.
        public MemorySegment stubConstructor(Arena arena, Linker linker, MethodHandles.Lookup lookup,
                                             Class<?> owner,
                                             Class<?>... arguments) {
            return sneaky((ThrowGet<? extends MemorySegment>) () -> linker.upcallStub(
                    lookup.findConstructor(owner, MethodType.methodType(owner, arguments)),
                    descriptor(), arena));
        }
    }

    static Func func() {
        return new Func();
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    interface ThrowMapper<I, O> {
        O apply(I i) throws Throwable;
    }

    interface ThrowApply<T> {
        T apply(Arena arena) throws Throwable;

        default <R> ThrowApply<R> then(ThrowMapper<T, R> m) {
            return a -> m.apply(apply(a));
        }
    }

    static <T> T sneaky(ThrowApply<T> action) {
        try (var arena = Arena.ofConfined()) {
            return action.apply(arena);
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface ByteThrowApply {
        byte apply(Arena arena) throws Throwable;
    }

    static byte sneakyByte(ByteThrowApply act) {
        try (var arena = Arena.ofConfined()) {
            return act.apply(arena);
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface ShortThrowApply {
        short apply(Arena arena) throws Throwable;

    }

    static short sneakyShort(ShortThrowApply act) {
        try (var arena = Arena.ofConfined()) {
            return act.apply(arena);
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface IntThrowApply {
        int apply(Arena arena) throws Throwable;
    }

    static int sneakyInt(IntThrowApply act) {
        try (var arena = Arena.ofConfined()) {
            return act.apply(arena);
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface LongThrowApply {
        long apply(Arena arena) throws Throwable;

    }

    static long sneakyLong(LongThrowApply act) {
        try (var arena = Arena.ofConfined()) {
            return act.apply(arena);
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface FloatThrowApply {
        float apply(Arena arena) throws Throwable;

    }

    static float sneakyFloat(FloatThrowApply act) {
        try (var arena = Arena.ofConfined()) {
            return act.apply(arena);
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface DoubleThrowApply {
        double apply(Arena arena) throws Throwable;

    }

    static double sneakyDouble(DoubleThrowApply act) {
        try (var arena = Arena.ofConfined()) {
            return act.apply(arena);
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface BooleanThrowApply {
        boolean apply(Arena arena) throws Throwable;

    }

    static boolean sneakyBoolean(BooleanThrowApply act) {
        try (var arena = Arena.ofConfined()) {
            return act.apply(arena);
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface VoidThrowApply {
        void apply(Arena arena) throws Throwable;
    }

    static void sneakyVoid(VoidThrowApply act) {
        try (var arena = Arena.ofConfined()) {
            act.apply(arena);
            return;
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface ThrowGet<T> {
        T get() throws Throwable;

        default <R> ThrowGet<R> then(ThrowMapper<T, R> m) {
            return () -> m.apply(get());
        }
    }

    static <T> T sneaky(ThrowGet<T> act) {
        try {
            return act.get();
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface ByteThrowGet {
        byte get() throws Throwable;
    }

    static byte sneakyByte(ByteThrowGet act) {
        try {
            return act.get();
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface ShortThrowGet {
        short get() throws Throwable;

    }

    static short sneakyShort(ShortThrowGet act) {
        try {
            return act.get();
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface IntThrowGet {
        int get() throws Throwable;

    }

    static int sneakyInt(IntThrowGet act) {
        try {
            return act.get();
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface LongThrowGet {
        long get() throws Throwable;
    }

    static long sneakyLong(LongThrowGet act) {
        try {
            return act.get();
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface FloatThrowGet {
        float get() throws Throwable;
    }

    static float sneakyFloat(FloatThrowGet act) {
        try {
            return act.get();
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface DoubleThrowGet {
        double get() throws Throwable;
    }

    static double sneakyDouble(DoubleThrowGet act) {
        try {
            return act.get();
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface BooleanThrowGet {
        boolean get() throws Throwable;
    }

    static boolean sneakyBoolean(BooleanThrowGet act) {
        try {
            return act.get();
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }

    interface VoidThrowGet {
        void get() throws Throwable;

    }

    static void sneakyVoid(VoidThrowGet act) {
        try {
            act.get();
            return;
        } catch (Throwable e) {
            sneakyThrow(e);
        }
        throw new IllegalStateException("should not reach");
    }
}
