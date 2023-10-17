package sk.tuke.meta.persistence;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * PersistenceManager allows to persist a set of entities into a database.
 * <p>
 * All methods of the interface may throw
 * {@link javax.persistence.PersistenceException}.
 * <p>
 * Implementations of this interface may require additional configuration
 * as constructor arguments, e.g. database connection.
 */
public interface PersistenceManager {

    /**
     * Create database tables for all managed entity classes.
     */
    void createTables() throws SQLException;

    /**
     * Get a specific entity based on the primary key.
     *
     * @param type entity class
     * @param id   primary key (id) value
     * @return the found entity or <code>Optional.empty()</code> if the entity does not exist
     */
    <T> Optional<T> get(Class<T> type, long id) throws SQLException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, ClassNotFoundException;

    /**
     * Get all entities of specified type.
     *
     * @param type entity class
     * @return a list of all entities stored in the database.
     */
    <T> List<T> getAll(Class<T> type) throws SQLException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException;

    /**
     * Get entities based on any field value.
     *
     * @param type      entity class
     * @param fieldName name of the field
     * @param value     searched field value
     * @return a list of entities where named field has specified value
     */
    <T> List<T> getBy(Class<T> type, String fieldName, Object value) throws SQLException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException;

    /**
     * Save entity into a database.
     * If entity has a non-zero identifier, manager would try to perform
     * <code>UPDATE</code>, otherwise <code>INSERT</code> would be performed.
     *
     * @param entity the entity to be saved
     * @return the value of primary key
     */
    long save(Object entity) throws IllegalAccessException, SQLException, ClassNotFoundException, InvocationTargetException, InstantiationException;

    /**
     * Delete the entity from the database, based on the primary key.
     *
     * @param entity the entity to be deleted
     */
    void delete(Object entity) throws SQLException, IllegalAccessException;
}
