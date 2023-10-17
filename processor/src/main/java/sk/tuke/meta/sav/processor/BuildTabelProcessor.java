package sk.tuke.meta.sav.processor;

import com.sun.jdi.ClassType;
import sk.tuke.meta.sav.processor.generation.SQLGenerator;
import sk.tuke.meta.sav.processor.generation.Generator;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.persistence.*;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Created by Milan on 13.2.2016.
 * Modified by Sergej Chodarev
 */

@SupportedAnnotationTypes("javax.persistence.Entity")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class BuildTabelProcessor extends AbstractProcessor {

    private List<Generator> generators = new LinkedList<>();
    private static String FILE_PATH = "sk.tuke.meta.example";


    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        generators.add(new SQLGenerator());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Entity.class);

        if (elements.size()>0){

            try {
                generateSQLTables(elements);
                generateSQLInsert(elements);
                generateSQLUpdate(elements);
                generateSQLGetById(elements);
                generateSQLGetList(elements);
                generateSQLDelete(elements);


            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    private void generateSQLDelete(Set<? extends Element> elements) throws IOException {
        for (Element el: elements) {
            saveSqlToFile(generateDeleteSql(el),
                    FILE_PATH+".delete",getTabelName(el)+"_DeleteQuery.sql");
        }
    }

    private void generateSQLGetList(Set<? extends Element> elements) throws IOException {

        for (Element el: elements) {
            saveSqlToFile(generateGetListSql(el,elements),
                    FILE_PATH+".getList",getTabelName(el)+"_GetListQuery.sql");
        }
    }

    private void generateSQLGetById(Set<? extends Element> elements) throws IOException {
        for (Element el: elements) {
            saveSqlToFile(generateGetByIdSql(el),
                    FILE_PATH+".getDetail",getTabelName(el)+"_GetDetailQuery.sql");
        }
    }


    private void generateSQLUpdate(Set<? extends Element> elements) throws IOException {
        for (Element el: elements) {
            saveSqlToFile(generateUpdateSql(el),
                    FILE_PATH+".update",getTabelName(el)+"_UpdateQuery.sql");
        }
    }
    private void generateSQLInsert(Set<? extends Element> elements) throws IOException {
        for (Element el: elements) {
            saveSqlToFile(generateInsertSql(el),
                    FILE_PATH+".insert",getTabelName(el)+"_InsertQuery.sql");
        }
    }


    private void generateSQLTables(Set<? extends Element>  generateElements) throws IOException {
        List<Element> elementList = new ArrayList<Element>();
        elementList.addAll(generateElements);
        List<String> queryList = new ArrayList<>();

        elementList.stream().forEach(element -> queryList.add(preprocessEntity(element,elementList)));
        saveSqlToFile(queryList,FILE_PATH,"Tables.sql");
    }

    private void saveSqlToFile(List<String> queryList, String path,String fileName) throws IOException {

        FileObject file = processingEnv.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT,path,fileName);
        OutputStream os = file.openOutputStream();

        queryList.stream().forEach(query-> {
            try {
                os.write(query.getBytes());
                os.write(System.lineSeparator().getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        os.close();
    }
    private void saveSqlToFile(String query, String path,String fileName) throws IOException {

        FileObject file = processingEnv.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT,path,fileName);
        OutputStream os = file.openOutputStream();
            try {
                os.write(query.getBytes());
                os.write(System.lineSeparator().getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        os.close();
    }

    private String preprocessEntity(Element entity,List<Element> elementList) {

        StringBuilder table = new StringBuilder();
        table.append(setTableHeader(entity));
        table.append(setTableFields(entity,elementList));

        table.append(setTableFooter());
        System.out.println(entity);
        return table.toString();
    }

    private String generateGetListSql(Element el,Set<? extends Element> entityList) {
        StringBuilder query = new StringBuilder();
        query.append("SELECT ");
        query.append(getAllFieldsQuery(el,entityList));
        int last = query.length()-2;
        query.replace(last, last + 2, "");
        query.append(" FROM ").append(getTableName(el));
        query.append(joinTables(el,entityList));

        return query.toString();
    }
    public StringBuilder joinTables(Element entity,Set<? extends Element> entityList){
        StringBuilder str = new StringBuilder();

        entity.getEnclosedElements().stream()
                .filter(element ->
                        element.getKind().equals(ElementKind.FIELD) &&
                                element.getAnnotation(ManyToOne.class)!=null)
                .forEach(f->{
                    for (AnnotationMirror annotationMirror :f.getAnnotationMirrors() ) {
                        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                                annotationMirror.getElementValues().entrySet()) {
                            if (entry.getKey().toString().equals("targetEntity()")) {
                                Object value = entry.getValue().getValue();
                                for (Element el: entityList) {
                                    if (el.toString().equals(value.toString())){
                                        str.append(makeRelation(entity,f.getSimpleName().toString(),el));
                                    }
                                }
                            }
                        }
                    }
                });


        return str;
    }

    public StringBuilder makeRelation(Element ParentClass, String connectionName,Element entity){
        StringBuilder str = new StringBuilder();

        entity.getEnclosedElements().stream()
                .filter(element ->
                        element.getKind().equals(ElementKind.FIELD) &&
                                element.getAnnotation(Id.class)!=null)
                .forEach(el ->{
                    str.append(" JOIN ")
                            .append(getTableName(entity)).append(" ON ");
                    str.append(getTableName(ParentClass))
                            .append(".").append(connectionName).append(" = ");
                    str.append(getTableName(entity)).append(".")
                            .append(getColumnName((VariableElement) el));
                } );
        return str;
    }
    public StringBuilder getAllFieldsQuery(Element entity,Set<? extends Element> entityList){

        StringBuilder str = new StringBuilder();
        entity.getEnclosedElements().stream().filter(element -> element.getKind()==ElementKind.FIELD).forEach(f->{
            if (f.getAnnotation(ManyToOne.class)!=null){
                for (AnnotationMirror annotationMirror :f.getAnnotationMirrors() ) {
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                            annotationMirror.getElementValues().entrySet()) {
                        if (entry.getKey().toString().equals("targetEntity()")) {
                            Object value = entry.getValue().getValue();
                            for (Element el: entityList) {
                                if (el.toString().equals(value.toString())){
                                    str.append(getAllFieldsQuery(el,entityList));
                                }
                            }
                        }
                    }
                }

            }else{
                str.append(getTableName(entity))
                        .append(".").append(getColumnName((VariableElement) f)).append(" as ")
                        .append(getTableName(entity)).append("_")
                        .append(getColumnName((VariableElement) f));
                str.append(", ");
            }
        });
        return str;
    }
    private String generateGetByIdSql(Element el) {
        VariableElement[] fields = el.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.FIELD).toList().toArray(new VariableElement[0]);

        return new StringBuilder().append("SELECT * FROM ")
                .append(getTableName(el))
                .append(" WHERE ").append(getColumnName(getIdField(fields)))
                .append(" = ?").toString();
    }
    private String generateDeleteSql(Element el) {
        VariableElement[] fields = el.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.FIELD).toList().toArray(new VariableElement[0]);

        return new StringBuilder().append("DELETE FROM ")
                .append(getTableName(el))
                .append(" WHERE ").append(getColumnName(getIdField(fields)))
                .append(" = ").append(" ? ").toString();
    }
    private String generateUpdateSql(Element el) {
        StringBuilder query = new StringBuilder();

        VariableElement[] fields = el.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.FIELD).toList().toArray(new VariableElement[0]);

        query.append("UPDATE ").append(getTableName(el)).append(" SET ");
        for (int i =0; i< fields.length;i++){

            if (fields[i].getAnnotation(Id.class)==null){
                if (fields[i].getAnnotation(Column.class)!=null){
                    query.append(fields[i].getAnnotation(Column.class).name()).append(" =").append(" ? ");
                }else{
                    query.append(fields[i].getSimpleName()).append(" =").append(" ? ");
                }
                query.append(i != fields.length - 1 ? " , " : " ");
            }
        }
        query.append(" WHERE ");

        if (getIdField(fields).getAnnotation(Column.class)!=null){
            query.append(getIdField(fields).getAnnotation(Column.class).name()).append(" = ? ");
        }else{
            query.append(getIdField(fields).getSimpleName()).append(" = ? ");
        }

        return query.toString();
    }
    private String generateInsertSql(Element entity) {
        StringBuilder query = new StringBuilder();

        query.append("INSERT INTO ").append(getTableName(entity));
        StringBuilder values = new StringBuilder();
        values.append(" VALUES ");
        query.append(" ( ");
        values.append(" ( ");

        List< VariableElement> va2 = (List<VariableElement>) entity.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.FIELD).toList();
        VariableElement[] field = va2.toArray(new VariableElement[0]);

        for (int i =0; i< field.length;i++){

            if (field[i].getAnnotation(Id.class)==null &&
                    field[i].getAnnotation(ManyToOne.class)==null){

                if (field[i].getAnnotation(Column.class) != null){
                    query.append(field[i].getAnnotation(Column.class).name());
                }else{
                    query.append(field[i].getSimpleName());
                }
                values.append(" ? ");
                query.append(i != field.length - 1 ? " , " : " ");
                values.append(i != field.length - 1 ? " , " : " ");
            } else if (field[i].getAnnotation(ManyToOne.class)!=null) {
                query.append(field[i].getSimpleName());
                values.append(" ? ");
                query.append(i != field.length - 1 ? " , " : " ");
                values.append(i != field.length - 1 ? " , " : " ");
            }
        }
        values.append(" ) ");
        query.append(" ) ").append(values);

        return query.toString();
    }

    private String setTableFields(Element entity, List<Element> elementList) {
        StringBuilder tablefields = new StringBuilder();
        StringBuilder prim = new StringBuilder();
        ArrayList< Object > relationArray = new ArrayList<>();
        ArrayList<VariableElement> fieldList = new ArrayList<>();

        entity.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.FIELD)
                .forEach(field -> {

                    addIdField((VariableElement) field, tablefields, prim);
                    addStringField((VariableElement) field, tablefields);
                    addIntegerField((VariableElement) field, tablefields);
                    addRelationField((VariableElement) field, fieldList ,tablefields, relationArray);
                });

        tablefields.append(setPrimaryAutoincrement(prim.toString()));

        for (Object obj:relationArray) {
            for (Element el:elementList) {

                if (obj.toString().equals(el.toString())){

                    tablefields.append(", CONSTRAINT fk_"+fieldList.get(0).getSimpleName()+ " FOREIGN KEY ( ")
                            .append(fieldList.get(0).getSimpleName()).append(" ) ").append("REFERENCES ");
                    tablefields.append(getTabelName(el));
                    tablefields.append("( ").append(getTabelIdVariable(el)).append(" ) ")
                            .append(" ON DELETE CASCADE ");
                }
            }
        }
        return tablefields.toString();
    }

    private String getTabelIdVariable(Element entity){

        StringBuilder str = new StringBuilder();

        entity.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.FIELD)
                .forEach(field -> {
                    generators.stream().filter(generator-> generator.isId((VariableElement) field))
                            .forEach(generator -> {
                                try {
                                    str.append( generator.writeColumnName( (VariableElement) field));
                                } catch (ClassNotFoundException e) {
                                    throw new RuntimeException(e);
                                }
                            });

                });

        return str.toString();
    }
    public VariableElement getIdField(VariableElement[] variableElements){

        for (VariableElement el: variableElements){
            if (el.getAnnotation(Id.class)!=null){
                return el;
            }
        }
        return null;
    }

    private String getTabelName(Element element){

        StringBuilder bldr = new StringBuilder();
        generators.forEach(generator -> {
            try {
                bldr.append(generator.writeTableName(element));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        return bldr.toString();
    }
    private String getColumnName(VariableElement element){

        StringBuilder bldr = new StringBuilder();
        generators.forEach(generator -> {
            try {
                bldr.append(generator.writeColumnName(element));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        return bldr.toString();
    }

    private String setPrimaryAutoincrement(String primaryKeyName){

        return " PRIMARY KEY ( " + primaryKeyName + " AUTOINCREMENT ) ";
    }
    private void addIdField(VariableElement field,StringBuilder tablefields,StringBuilder prim){
        generators.stream().filter(generator-> generator.isId( field))
                .forEach(generator -> {
                    try {
                        prim.append( generator.writeColumnName( field));
                        tablefields.append(generator.writeColumnName(field))
                                .append(" INTEGER NOT NULL UNIQUE ,");
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
    private void addStringField(VariableElement field,StringBuilder tablefields) {
        generators.stream().filter(generator-> {
                            try {return generator.isString( field);
                            } catch (ClassNotFoundException e) {
                                throw new RuntimeException(e);
                            }
                })
                .forEach(generator -> {
                    try {
                        tablefields.append(generator.writeColumnName(field))
                                .append(generator.writeColumnConfig(field,"VARCHAR"))
                                .append(" , ");
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
    private void addIntegerField(VariableElement field,StringBuilder tablefields){
        generators.stream().filter(generator-> generator.isInteger( field))
                .forEach(generator -> {
                    try {
                        tablefields.append(generator.writeColumnName(field))
                                .append(generator.writeColumnConfig(field,"INTEGER"))
                                .append(" , ");
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }

                });
    }
    private void addRelationField(VariableElement field,ArrayList<VariableElement> fieldList,StringBuilder tablefields,
                                  ArrayList<Object> relationClassArr){

        generators.stream().filter(generator-> generator.isRelation( field))
                .forEach(generator -> {
                    try {
                        relationClassArr.add(generator.getRelationClass(field));
                        fieldList.add(field);
                        tablefields.append(generator.writeColumnName(field))
                                .append(" INTEGER NOT NULL ,");
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }

                });
    }

    private String setTableHeader(Element entity){
        StringBuilder headerText = new StringBuilder("CREATE TABLE ");
        generators.forEach(generator -> {
            headerText.append(getTableName(entity));
        });
        headerText.append("(");
        return headerText.toString();
    }
    private String getTableName(Element entity){
        AtomicReference<String> result = new AtomicReference<>("");
        generators.forEach(generator -> {
            try {
                result.set(generator.writeTableName(entity));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

        });
        return result.toString();
    }

    private String setTableFooter(){
        return ");";
    }

    private String footer() {
        return "    }\n";
    }




}
