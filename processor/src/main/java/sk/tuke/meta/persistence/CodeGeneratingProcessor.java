package sk.tuke.meta.persistence;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.*;
import javax.persistence.*;
import javax.tools.Diagnostic;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SupportedAnnotationTypes("javax.persistence.Entity")
public class CodeGeneratingProcessor extends AbstractProcessor {
    private static final String TEMPLATE_PATH = "sk/tuke/meta/persistence/" ;

    private VelocityEngine velocity;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        velocity = new VelocityEngine();
        velocity.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocity.setProperty("classpath.resource.loader.class",
                ClasspathResourceLoader.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {
        var entityTypes = roundEnv.getElementsAnnotatedWith(Entity.class);

        for (var entity: entityTypes) {
            try {
                generateDAOClass((TypeElement) entity,entityTypes);
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,e.getMessage());
            }
        }

        if (entityTypes.size()>0){
            try {
                generatePersistanceManager(entityTypes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


        return true;
    }
    private void generatePersistanceManager(Set<? extends Element> entityTypes) throws IOException {
        var jfo = processingEnv.getFiler().createSourceFile("GeneratedPersistenceManager");

        try(Writer writer = jfo.openWriter()){
            var tenplate = velocity.getTemplate(TEMPLATE_PATH + "GeneratedPersistenceManager.java.vm");
            var context = new VelocityContext();
//            mainList.get(0).getEnclosedElements();
            List<String> names = new ArrayList<>();
            List<String> fullNames = new ArrayList<>();

            for (Element s: entityTypes) {
                names.add(s.getSimpleName().toString());
                fullNames.add(s.toString());
            }

            context.put("entities",entityTypes.toArray() );
            context.put("entityNames",names);
            context.put("entityPaths",fullNames);


            context.put("proj", "JAKATRA");
//            context.put("entity", entityType.getSimpleName().toString());
            tenplate.merge(context,writer);
        }
    }

    private void generateDAOClass(TypeElement entityType,Set<? extends Element> entityTypes) throws IOException {
        var jfo = processingEnv.getFiler().createSourceFile(entityType.toString() + "DAO");


        try(Writer writer = jfo.openWriter()){
            var tenplate = velocity.getTemplate(TEMPLATE_PATH + "DAO.java.vm");
            var context = new VelocityContext();

            context.put("package", entityType.getEnclosingElement().toString());
            context.put("entity", entityType.getSimpleName().toString());
            context.put("relationClassName",getRelationClassNameListt(entityType,entityTypes));
            context.put("relationStrategy",getRelationClassNameStrategy(entityType,entityTypes));
            context.put("relationClassType",getRelationFieldClass(entityType));

//            context.put("relationClassName","Department");
//            context.put("relationStrategy","LAZY");
//            context.put("relationClassType","getRelationFieldClass(entityType)");

            context.put("tableName", getTabelName(entityType));
            context.put("entityFields", getEntityFields(entityType));
            context.put("entityIdColumn", getColumnName((getIdField(entityType))));
            context.put("entityIdColumnDB", getdbColumnName((getIdField(entityType))));

            context.put("relationShipColumn", getColumnName((getRelationField(entityType))));
            context.put("relationShipColumnDB", getdbColumnName((getRelationField(entityType))));

            context.put("entitySetters", getSettersVariables(entityType));

//            context.put("relationClassName",getRelationClassNameListt().getSimpleName().toString());
//            context.put("relationClassName","Department");



            context.put("stringVariablesList", getStringVariablesList(entityType));
            context.put("intVariablesList", getIntVariablesList(entityType));

            Element relationElement = getRelationClassNameList(entityType,entityTypes);

            if (relationElement!=null){
                System.out.println(relationElement.getSimpleName().toString());

                context.put("relationEntitySetters", getSettersVariables(relationElement));
                context.put("relationTableName", getTabelName(relationElement));
                context.put("relationEntityIdColumnDB", getdbColumnName((getIdField(relationElement))));
                context.put("relationStringVariablesList", getStringVariablesList(relationElement));
                context.put("relationIntVariablesList", getIntVariablesList(relationElement));


            }




            context.put("insertSQL",getInsertQuery(entityType));
            context.put("updateSQL",getUpdateQuery(entityType));
            context.put("deleteSQL",getDeleteQuery(entityType));
            context.put("creatTableSQL", getCreatTableQuery(entityType));
            context.put("getDetailSQL", getDetailQuery(entityType));
            context.put("getListSQL", getListQuery(entityType));


            tenplate.merge(context,writer);
        }
    }




    private List<String> getStringVariablesList(Element entityType) {
        List<String> str = new ArrayList<>();
        for (Element el: entityType.getEnclosedElements()) {
            if(el.asType().toString().equals(String.class.getName()) &&
            el.getKind().equals(ElementKind.FIELD)){
                str.add(getdbColumnName((VariableElement) el));
            }
        }

        return str;
    }
    private List<String> getIntVariablesList(Element entityType) {
        List<String> str = new ArrayList<>();
        for (Element el: entityType.getEnclosedElements()) {
            if(el.asType().toString().equals(int.class.getName())&&
                    el.getKind().equals(ElementKind.FIELD)){
                str.add(getdbColumnName((VariableElement) el));
            }
        }

        return str;
    }
    private Element getRelationClassNameList(Element elem, Set<? extends Element> elementList) {

        VariableElement field = getRelationField(elem);
        if (field !=null){
            for (AnnotationMirror annotationMirror :field.getAnnotationMirrors() ) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                        annotationMirror.getElementValues().entrySet()) {
                    if (entry.getKey().toString().equals("targetEntity()")) {
                        Object value = entry.getValue().getValue();
                        for (Element el: elementList) {
                            if (el.toString().equals(value.toString())){
                                return el;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private String getRelationClassNameListt(Element elem, Set<? extends Element> elementList) {

        VariableElement field = getRelationField(elem);
        if (field !=null){
            for (AnnotationMirror annotationMirror :field.getAnnotationMirrors() ) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                        annotationMirror.getElementValues().entrySet()) {
                    if (entry.getKey().toString().equals("fetch()")){
                        Object value = entry.getValue().getValue();
                        System.out.println();

                    }

                    if (entry.getKey().toString().equals("targetEntity()")) {
                        Object value = entry.getValue().getValue();
                        for (Element el: elementList) {
                            if (el.toString().equals(value.toString())){
                                return value.toString();
                            }
                        }
                    }
                }
            }
        }
        return "";
    }

    private String getRelationClassNameStrategy(Element elem, Set<? extends Element> elementList) {

        VariableElement field = getRelationField(elem);
        if (field !=null){
            for (AnnotationMirror annotationMirror :field.getAnnotationMirrors() ) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                        annotationMirror.getElementValues().entrySet()) {
                    if (entry.getKey().toString().equals("fetch()")){
                        return entry.getValue().getValue().toString();
                    }
                }
            }
        }
        return "";
    }
    private String getRelationFieldClass(Element elem){

        for (Element lll:elem.getEnclosedElements()) {
            if (lll.getAnnotation(ManyToOne.class)!=null && lll.getKind().equals(ElementKind.FIELD)){
                return lll.asType().toString();
            }
        }
        return "";
    }



    private List<String> getEntityFields(TypeElement entityType){
        List<String> strList = new ArrayList<>();
        entityType.getEnclosedElements().stream().filter(el-> el.getKind().equals(ElementKind.FIELD))
                .forEach(item-> {
                    strList.add(item.getSimpleName().toString());
                });

        return strList;
    }

    private VariableElement getRelationField(Element variableElement) {
        VariableElement[] fields = variableElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.FIELD).toList().toArray(new VariableElement[0]);

        for (VariableElement el: fields){
            if (el.getAnnotation(ManyToOne.class)!=null){
                return el;
            }
        }
        return null;
    }
    public VariableElement getIdField(Element variableElement){

        VariableElement[] fields = variableElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.FIELD).toList().toArray(new VariableElement[0]);

        for (VariableElement el: fields){
            if (el.getAnnotation(Id.class)!=null){
                return el;
            }
        }
        return null;
    }
    private String getColumnName(VariableElement element){

        if (element==null){
            return "";
        }
        return element.getSimpleName().toString();

    }

    private Map<String,String> getSettersVariables(Element entityType) {
        HashMap<String,String> hm = new HashMap<>();

        for (Element elem : entityType.getEnclosedElements()) {
            if (elem.getKind().equals(ElementKind.FIELD)){
                String columnName = getdbColumnName((VariableElement) elem);
                for (Element elemMethod: entityType.getEnclosedElements()) {
                    if (elemMethod.getKind().equals(ElementKind.METHOD) &&
                            elemMethod.getSimpleName().toString().toLowerCase().contains("set") &&
                            elemMethod.getSimpleName().toString().toLowerCase()
                                    .contains(elem.getSimpleName().toString().toLowerCase())){

                        hm.put(columnName,elemMethod.getSimpleName().toString());
                    }
                }

            }
        }

         return hm;
    }

    private String getdbColumnName(VariableElement elem){
        if (elem==null){
            return "";
        }
        if (elem.getAnnotation(Column.class)!=null){
            if (elem.getAnnotation(Column.class).name().equals("")){
                return elem.getSimpleName().toString();
            }
            else{
                return elem.getAnnotation(Column.class).name();
            }
        }

        return elem.getSimpleName().toString();
    }


    private String getTabelName(Element element){

        if (element.getAnnotation(Table.class)!=null){
            String nameValue = element.getAnnotation(Table.class).name();
            if (nameValue.length()>0){
                return nameValue;
            }else {
                return element.getSimpleName().toString();
            }
        }

        return element.getSimpleName().toString();
    }

    private String getListQuery(Element type) {
        StringBuilder sql = new StringBuilder();
        readGeneratedQueries("sk/tuke/meta/example/getList/" + getTabelName(type)+"_GetListQuery.sql")
                .forEach(sql::append);
        return sql.toString();
    }
    private String getDetailQuery(Element type) {
        StringBuilder sql = new StringBuilder();

        readGeneratedQueries("sk/tuke/meta/example/getDetail/" + getTabelName(type)+"_GetDetailQuery.sql")
                .forEach(sql::append);
        return sql.toString();
    }

    private String getCreatTableQuery(Element element){

        return readGeneratedQueries("sk/tuke/meta/example/Tables.sql").stream()
                .filter(queryTable ->
                        extractTableName(queryTable).equals(getTabelName(element))).findFirst().get();


    }
    private String getInsertQuery(Element type){
        StringBuilder sql = new StringBuilder();


        readGeneratedQueries("sk/tuke/meta/example/insert/" + getTabelName(type)+"_InsertQuery.sql")
                .forEach(sql::append);
        return sql.toString();
    }
    private String getUpdateQuery(Element type){
        StringBuilder sql = new StringBuilder();

        readGeneratedQueries("sk/tuke/meta/example/update/" + getTabelName(type)+"_UpdateQuery.sql")
                .forEach(sql::append);
        return sql.toString();
    }

    private String getDeleteQuery(Element type) {
        StringBuilder sql = new StringBuilder();

        readGeneratedQueries("sk/tuke/meta/example/delete/" + getTabelName(type)+"_DeleteQuery.sql")
                .forEach(sql::append);
        return sql.toString();
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
        if (matcher.find()) {tableName = matcher.group(1);}

        return tableName;
    }







}
