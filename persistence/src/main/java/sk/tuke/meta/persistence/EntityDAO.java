package sk.tuke.meta.persistence;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface EntityDAO<T> {
    void createTable() throws SQLException;

    T get(long id) throws SQLException, InvocationTargetException, InstantiationException, IllegalAccessException, ClassNotFoundException;

    List<T> getAll() throws SQLException, InvocationTargetException, InstantiationException, IllegalAccessException;

    List<T> getBy(String fieldName, Object value) throws SQLException, InvocationTargetException, InstantiationException, IllegalAccessException;

    long save(Object entity) throws SQLException, IllegalAccessException, ClassNotFoundException, InvocationTargetException, InstantiationException;

    void delete(Object entity) throws IllegalAccessException, SQLException;
}
