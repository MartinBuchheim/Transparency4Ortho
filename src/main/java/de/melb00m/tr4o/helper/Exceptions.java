package de.melb00m.tr4o.helper;


public final class Exceptions {

    private Exceptions() {}

    /**
     * Sneaky method that tricks the compiler into passing checked exceptions through methods that don't
     * explicitly handle or declare them.
     * <p/>
     * Unlike wrapping them into a {@link RuntimeException}, the checked exception stays intact and can be handled
     * further down the line by clients that expect them.
     *
     * @param throwable Throwable to pass on
     * @param <T> The throwable type
     * @return The given exception (invisible for compiler at compile-time)
     * @throws T
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException uncheck(Throwable throwable) throws T {
        throw (T) throwable;
    }

}
