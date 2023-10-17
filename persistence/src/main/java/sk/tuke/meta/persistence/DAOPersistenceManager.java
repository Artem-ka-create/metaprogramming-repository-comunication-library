package sk.tuke.meta.persistence;


import javax.persistence.ManyToOne;
import java.lang.reflect.Field;
import sk.tuke.meta.persistence.DAOPersistenceManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;



public class DAOPersistenceManager implements PersistenceManager {
    private final Connection connection;
    private final Map<Class<?>, EntityDAO<?>> daos = new LinkedHashMap<>();

    public DAOPersistenceManager(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("unchecked")
    public <T> EntityDAO<T> getDAO(Class<T> type) {
        // Types are checked in put DAO method to match properly,
        // so the cast should be OK
        return (EntityDAO<T>) daos.get(type);
    }

    protected <T> void putDAO(Class<T> type, EntityDAO<T> dao) {
        daos.put(type, dao);
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    public void createTables() throws SQLException {
        setDbConfiguration();
        for (var dao : daos.values()) {
                dao.createTable();
        }
    }

    @Override
    public <T> Optional<T> get(Class<T> type, long id) throws SQLException, InvocationTargetException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        return Optional.of(getDAO(type).get(id));
    }

    @Override
    public <T> List<T> getAll(Class<T> type) throws SQLException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return getDAO(type).getAll();
    }

    @Override
    public <T> List<T> getBy(Class<T> type, String fieldName, Object value) throws SQLException, InvocationTargetException, InstantiationException, IllegalAccessException  {
        return getDAO(type).getBy(fieldName, value);
    }

    @Override
    public long save(Object entity) throws SQLException, IllegalAccessException, ClassNotFoundException, InvocationTargetException, InstantiationException {
        // TODO: What if we would receive a Proxy?

        if (Proxy.isProxyClass(entity.getClass())){
            EntityProxy.EntityIH ih = (EntityProxy.EntityIH) Proxy.getInvocationHandler(entity);
            Object obj = ih.getObj();

            return getDAO(obj.getClass()).save(obj);
        }

        return getDAO(entity.getClass()).save(entity);
    }

    @Override
    public void delete(Object entity) throws SQLException, IllegalAccessException {
        getDAO((entity.getClass())).delete(entity);
    }

    public void  setDbConfiguration () throws SQLException {
        PreparedStatement pstmt = connection.prepareStatement("PRAGMA foreign_keys = ON;");
        pstmt.execute();
    }
}
