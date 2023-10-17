package sk.tuke.meta.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.Arrays;

public class EntityProxy {
    public static Object createProxy(Class<?> type,long id,DAOPersistenceManager rpm) {
        System.out.println(Arrays.toString(type.getInterfaces()));
        return Proxy.newProxyInstance(type.getClassLoader(),
                type.getInterfaces(),
                new EntityIH(type,id,rpm));
    }

    public static class EntityIH implements InvocationHandler {
        private Object target;

        private final Class<?> type;
        private final long id;
        private final DAOPersistenceManager rpm;
        public EntityIH(Class<?> type,long id,DAOPersistenceManager rpm) {
            target=null;
            this.type = type;
            this.id = id;
            this.rpm = rpm;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = null;
            try {
                if (rpm.get(type,id).isPresent()){
                    target = rpm.get(type,id).get();
                    System.out.println("PROXY-->" + target );
                    result = method.invoke(target, args);
                }
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            return result;
        }
        public Object getObj() throws SQLException, ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException {
            return rpm.get(type,id).get();
        }
    }

}
