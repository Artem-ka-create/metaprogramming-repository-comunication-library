package sk.tuke.meta.persistence;

import javax.persistence.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
//import sk.tuke.meta.example.TableGenerator;


public class ReflectivePersistenceManager implements PersistenceManager {
    public static Connection connection;
    public static Class<?>[] types;

    public ReflectivePersistenceManager(Connection connection, Class<?>... types) {
        this.connection = connection;
        this.types = types;
    }

    @Override
    public void createTables() throws SQLException, NullPointerException {

        ArrayList<String> tableNames = getDBTables(connection);
        setDbConfiguration();

        readGeneratedQueries("sk/tuke/meta/example/Tables.sql").stream()
                .forEach(queryTable -> {
                    if (!tableNames.contains(extractTableName(queryTable))){
                        try {
                            PreparedStatement psmt = connection.prepareStatement(queryTable);
                            psmt.execute();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
    }



    @Override
    public <T> Optional<T> get(Class<T> type, long id) throws SQLException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {

        String query = String.valueOf(generateSelectQuery(type));
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setString(1, String.valueOf(id));
        ResultSet resultSet = pstmt.executeQuery();

        if (resultSet.next()){

            Constructor<T> constructor = type.getDeclaredConstructor();
            T obj = constructor.newInstance();

            for (Field f : type.getDeclaredFields()) {

                f.setAccessible(true);
                if(f.isAnnotationPresent(ManyToOne.class)){
                    Class<?> tagetClass = f.getAnnotation(ManyToOne.class).targetEntity();

                    if (f.getDeclaredAnnotation(ManyToOne.class).fetch().equals(FetchType.LAZY)){
//                        Object proxy = EntityProxy
//                                .createProxy(tagetClass,resultSet.getLong(f.getName()),this);
//                        f.set(obj,proxy);
                    }else{
                        f.set(obj,get(tagetClass,resultSet.getLong(f.getName())).get());
                    }
                }
                else {
                    if (f.getType() == String.class){
                        f.set(obj, resultSet.getString(getColumnNameByField(f)));
                    }
                    else if (f.getType() == int.class) {
                        f.set(obj, resultSet.getInt(getColumnNameByField(f)));
                    }
                    else if (f.getType() == long.class) {
                        f.set(obj, resultSet.getLong(getColumnNameByField(f)));
                    }
                }
            }
            return Optional.of(obj);
        }
        return Optional.empty();
    }

    @Override
    public <T> List<T> getAll(Class<T> type) throws SQLException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {

        ArrayList<T> allObj = new ArrayList<>();
        String query = String.valueOf(generateSelectAllQuery(type));
        System.out.println(query);

        PreparedStatement pstmt = connection.prepareStatement(query);
        ResultSet resultSet = pstmt.executeQuery();

        while (resultSet.next()){
            allObj.add(createEntity(type,resultSet).get());
        }

        return allObj;
    }

    @Override
    public <T> List<T> getBy(Class<T> type, String fieldName, Object value) throws SQLException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {

        List<T> objectList = new ArrayList<>();
        String query = generateSelectAllQuery(type)
                + " WHERE " +getTableName(type)+"."
                + fieldName + " = " + "\'"
                + value + "\'";

        PreparedStatement pstmt = connection.prepareStatement(query);

        ResultSet resultSet = pstmt.executeQuery();
        while (resultSet.next()){
            objectList.add(createEntity(type,resultSet).get());
        }

        return objectList;
    }

    @Override
    public long save(Object entity) throws IllegalAccessException, SQLException {

        ArrayList<Field> valList = new ArrayList<>();
        String relFieldName ="";
        long relId = 0;

        for (Field fld: entity.getClass().getDeclaredFields()) {

            if (fld.isAnnotationPresent(ManyToOne.class)){
                relFieldName = fld.getName();
                fld.setAccessible(true);
                relId = save(fld.get(entity));
            }
            valList.add(fld);
        }

        String entId  = String.valueOf(getObjectId(entity.getClass(),entity));
        boolean existsStatus = entId.equals("0");

        String query = generateSaveQuery(entity.getClass(),existsStatus? "insert" : "update");

        PreparedStatement pstmt = connection.prepareStatement(query);

        // fill values to db
        int loadIndex=1;
        for (Field f : valList) {

            if (!f.isAnnotationPresent(Id.class)){
                f.setAccessible(true);
                if (f.getName().equals(relFieldName)){
                    pstmt.setLong(loadIndex, relId);
                }else{pstmt.setString(loadIndex,  String.valueOf(f.get(entity)));}
                loadIndex+=1;
            }
        }

        //for update
        if (!existsStatus){
            pstmt.setLong(valList.size(),getObjectId(entity.getClass(),entity));
        }

        try {
            if (pstmt.executeUpdate() == 1 && existsStatus){

                ResultSet res = pstmt.getGeneratedKeys();
                if (res.next()){

                    setObjectId(entity.getClass(),entity,res.getString(1));
                }
            }

        }catch (SQLException e){
            System.out.println(e);
        }
        return getObjectId(entity.getClass(),entity);

    }

    @Override
    public void delete(Object entity) throws SQLException, IllegalAccessException {

        String query = String.valueOf(generateDeleteQuery(entity.getClass()));
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setString(1,Long.toString(getObjectId(entity.getClass(),entity)));
        pstmt.executeUpdate();

    }


    private ArrayList<String> readGeneratedQueries(String path) {
        ArrayList<String> sqls = new ArrayList<>();

        InputStream sqlStrm = getClass().getClassLoader().getResourceAsStream(path);
        if (sqlStrm != null){
            InputStreamReader isr = new InputStreamReader(sqlStrm,
                    StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader (isr);
            br.lines().forEach(line -> sqls.add(line));
        }
        return sqls;
    }

    public static String extractTableName(String query) {
        String tableName = "";

        Pattern pattern = Pattern.compile("\\bCREATE\\s+TABLE\\s+([\\w\\d_]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);

        if (matcher.find()) {
            tableName = matcher.group(1);
        }

        return tableName;
    }

    public <T> Optional<T> createEntity(Class<T> type,ResultSet resultSet) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, SQLException {

        Constructor<T> constructor = type.getDeclaredConstructor();
        T obj = constructor.newInstance();

        for (Field f : type.getDeclaredFields()) {

            f.setAccessible(true);
            if(f.isAnnotationPresent(ManyToOne.class)){

                Class<?> targetClass = f.getDeclaredAnnotation(ManyToOne.class).targetEntity();

                if (f.getDeclaredAnnotation(ManyToOne.class).fetch().equals(FetchType.LAZY)){

//                    Object proxy = EntityProxy.createProxy(
//                            targetClass,
//                            resultSet.getLong(getTableName(targetClass)+"_"+getColumnName(getIdField(targetClass))),
//                            this);
//                    f.set(obj,proxy);
                }else {
                    f.set(obj, createEntity(targetClass,resultSet).get());
                }


            }
            else {
                if (f.getType()== String.class){
                    f.set(obj, resultSet.getString(getTableName(type)+"_"
                            +getColumnName(f)));
                }
                else if (f.getType()== int.class) {
                    f.set(obj, resultSet.getInt(getTableName(type)+"_"
                            +getColumnName(f)));
                }
                else if (f.getType()== long.class) {
                    f.set(obj, resultSet.getLong(getTableName(type)+"_"
                            +getColumnName(f)));
                }
            }
        }
        return Optional.of(obj);
    }

    public String generateSaveQuery (Class<?> entity,String operation) throws NullPointerException {
        StringBuilder query = new StringBuilder();
        Field[] field = entity.getDeclaredFields();

        if (operation.toUpperCase().equals("INSERT")){


            query.append("INSERT INTO ").append(getTableName(entity));
            StringBuilder values = new StringBuilder();
            values.append(" VALUES ");

            query.append(" ( ");
            values.append(" ( ");

            for (int i =0; i< field.length;i++){

                if (!field[i].isAnnotationPresent(Id.class) &&
                        !field[i].isAnnotationPresent(ManyToOne.class)){

                    if (field[i].isAnnotationPresent(Column.class)){
                        query.append(field[i].getAnnotation(Column.class).name());
                    }else{
                        query.append(field[i].getName());
                    }
                    values.append(" ? ");
                    query.append(i != field.length - 1 ? " , " : " ");
                    values.append(i != field.length - 1 ? " , " : " ");
                } else if (field[i].isAnnotationPresent(ManyToOne.class)) {
                    query.append(field[i].getName());
                    values.append(" ? ");
                    query.append(i != field.length - 1 ? " , " : " ");
                    values.append(i != field.length - 1 ? " , " : " ");
                }
            }
            values.append(" ) ");
            query.append(" ) ").append(values);

        }
        else if (operation.toUpperCase().equals("UPDATE")){
            query.append("UPDATE ").append(getTableName(entity)).append(" SET ");

            for (int i =0; i< field.length;i++){

                if (!field[i].isAnnotationPresent(Id.class)){
                    if (field[i].isAnnotationPresent(Column.class)){
                        query.append(field[i].getDeclaredAnnotation(Column.class).name()).append(" =").append(" ? ");
                    }else{
                        query.append(field[i].getName()).append(" =").append(" ? ");
                    }
                    query.append(i != field.length - 1 ? " , " : " ");
                }
            }
            query.append(" WHERE ");

            if (getIdField(entity).isAnnotationPresent(Column.class)){
                query.append(getIdField(entity).getAnnotation(Column.class).name()).append(" = ? ");
            }else{
                query.append(getIdField(entity).getName()).append(" = ? ");
            }

        }
        else{

            return null;
        }

        return query.toString();
    };
    public String getTableName(Class<?> type){

        if (type.isAnnotationPresent(Table.class)){

            return type.getDeclaredAnnotation(Table.class).name().length() > 0 ?
                    type.getDeclaredAnnotation(Table.class).name():
                    type.getSimpleName();

        }else{
           return type.getSimpleName();
        }
    }
    public String getColumnName(Field field){

        if (field.isAnnotationPresent(Column.class)){
            return field.getDeclaredAnnotation(Column.class).name().length()>0?
                    field.getDeclaredAnnotation(Column.class).name() :
                    field.getName();
        }else{
            return field.getName();
        }
    }

    public String getColumnNameByField(Field fld){
        if (fld.isAnnotationPresent(Column.class)){
            return fld.getAnnotation(Column.class).name();
        }
        else{
            return fld.getName();
        }
    }

    public StringBuilder generateDeleteQuery(Class<?> entityClass){
        return new StringBuilder().append("DELETE FROM ")
                .append(getTableName(entityClass))
                .append(" WHERE ").append(getColumnNameByField(getIdField(entityClass)))
                .append(" = ").append(" ? ");
    }
    public StringBuilder generateSelectQuery(Class<?> entityClass){
        return new StringBuilder().append("SELECT * FROM ")
                .append(getTableName(entityClass))
                .append(" WHERE ").append(getColumnNameByField(getIdField(entityClass)))
                .append(" = ?");
    }
    public StringBuilder generateSelectAllQuery(Class<?> entityClass){
        StringBuilder query = new StringBuilder();
        query.append("SELECT ");
        query.append(getAllFieldsQuery(entityClass));
        int last = query.length()-2;
        query.replace(last, last + 2, "");
        query.append(" FROM ").append(getTableName(entityClass));
        query.append(joinTables(entityClass));

        return query;
    }

    public StringBuilder joinTables(Class<?> entity){
        StringBuilder str = new StringBuilder();

        for (Field f:entity.getDeclaredFields()) {
            if (f.isAnnotationPresent(ManyToOne.class)){
                Class<?> targetClass = f.getDeclaredAnnotation(ManyToOne.class).targetEntity();
//                str.append(makeRelation(entity,f.getName(),f.getType()));
                str.append(makeRelation(entity,f.getName(),targetClass));
            }
        }
        return str;
    }

    public StringBuilder makeRelation(Class<?> ParentClass, String connectionName,Class<?> entity){
        StringBuilder str = new StringBuilder();
        for (Field f :entity.getDeclaredFields()) {
            if (f.isAnnotationPresent(Id.class)){
                str.append(" JOIN ")
                        .append(getTableName(entity)).append(" ON ");
                str.append(getTableName(ParentClass))
                        .append(".").append(connectionName).append(" = ");
                str.append(getTableName(entity)).append(".")
                        .append(getColumnNameByField(f));
            }
        }

        return str;
    }

    public StringBuilder getAllFieldsQuery(Class<?> entity){

        StringBuilder str = new StringBuilder();

        for (Field f: entity.getDeclaredFields()) {

            if (f.isAnnotationPresent(ManyToOne.class)){
                Class<?> targetClass = f.getDeclaredAnnotation(ManyToOne.class).targetEntity();
//                str.append(getAllFieldsQuery(f.getType()));
                str.append(getAllFieldsQuery(targetClass));

            }else{
                str.append(getTableName(entity))
                        .append(".").append(getColumnNameByField(f)).append(" as ")
                        .append(getTableName(entity)).append("_")
                        .append(getColumnNameByField(f));
                str.append(", ");
            }
        }
        return str;
    }

    public void setObjectId (Class<?> objectClass,Object entity,String value) throws IllegalAccessException {
        ArrayList<Field> depField = getVariableByAnnotation(Id.class,objectClass);
        depField.get(0).setAccessible(true);
        depField.get(0).set(entity,Integer.parseInt(value));
    }
    public long getObjectId (Class<?> objectClass,Object entity) throws IllegalAccessException {

        ArrayList<Field> fields = getVariableByAnnotation(Id.class,objectClass);
        Field fff = fields.get(0);
        fields.get(0).setAccessible(true);
        return (long) fff.get(entity);
    }

    public Field getIdField(Class<?> entityClass){

        for (Field f : entityClass.getDeclaredFields()) {
            if (f.isAnnotationPresent(Id.class)){
                return f;
            }
        }
        return null;
    }
    public ArrayList<Field> getVariableByAnnotation (Class annotationClass,Class<?> entityClass){
        ArrayList<Field> fields = new ArrayList<>();
        for (Field f : entityClass.getDeclaredFields()) {
            if (f.isAnnotationPresent(annotationClass))
                fields.add(f);
        }
        return fields;
    }
    public ArrayList<String> getDBTables( Connection connection) throws SQLException {
        ArrayList<String> names = new ArrayList<>();

        DatabaseMetaData dbm = connection.getMetaData();

        String[] types = {"TABLE"};
        //Retrieving the columns in the database
        ResultSet tables = dbm.getTables(null, null, "%", types);
        while (tables.next()) {
            names.add(tables.getString("TABLE_NAME"));
        }

        return names;
    }
    public void  setDbConfiguration () throws SQLException {
        PreparedStatement pstmt = connection.prepareStatement("PRAGMA foreign_keys = ON;");
        pstmt.execute();
    }


}
