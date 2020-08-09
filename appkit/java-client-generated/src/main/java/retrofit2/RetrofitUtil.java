package retrofit2;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * This class provides access to package-private {@link Retrofit#loadServiceMethod(Method)}
 * which is required to bypass usual proxy generation which doesn't work under Graal native-image.
 *
 * @see "ErgoNodeFacade in lib-impl"
 */
public class RetrofitUtil {
    static private final Object[] emptyArgs = new Object[0];

    @Nullable
    static public <T> Call<T> invokeServiceMethod(Retrofit r, Method m, Object[] args) {
        return (Call<T>)r.loadServiceMethod(m).invoke(args != null ? args : emptyArgs);
    }
}
