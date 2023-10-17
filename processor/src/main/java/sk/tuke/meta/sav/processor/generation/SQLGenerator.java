package sk.tuke.meta.sav.processor.generation;

import javax.lang.model.element.*;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Map;


public class SQLGenerator implements Generator {

    @Override
    public boolean isId(VariableElement field) { return field.getAnnotation(Id.class) != null; }

    @Override
    public boolean isRelation(VariableElement field) { return field.getAnnotation(ManyToOne.class) != null; }

    @Override
    public boolean isInteger(VariableElement field) {
        return field.asType().toString().equals(int.class.getName());
    }

    @Override
    public boolean isString(VariableElement field) throws ClassNotFoundException {
        return field.asType().toString().equals(String.class.getName());
    }

    @Override
    public Object getRelationClass(VariableElement field) {

        if (field.getAnnotation(ManyToOne.class)!=null){

            for (AnnotationMirror annotationMirror :field.getAnnotationMirrors() ) {

                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                        annotationMirror.getElementValues().entrySet()) {
                    if (entry.getKey().toString().equals("targetEntity()")) {
                        return entry.getValue().getValue();
                    }
                }
            }
        }

        return field.asType();
    }

    @Override
    public String writeColumnConfig(VariableElement field, String opperation) {
        StringBuilder config = new StringBuilder(" " + opperation + " ");

        if (field.getAnnotation(Column.class) != null){

            if (field.getAnnotation(Column.class).unique()){
                config.append(" UNIQUE ");
            }
            if (field.getAnnotation(Column.class).nullable()){
                config.append(" NOT NULL ");
            }
        }
        return config.toString();
    }

    @Override
    public String writeColumnName(VariableElement field) throws ClassNotFoundException {

        if (field.getAnnotation(Column.class)!=null){
            String nameValue = field.getAnnotation(Column.class).name();
            if (nameValue.length()>0){
                return nameValue;
            }
            return field.getSimpleName().toString();
        }
        return field.getSimpleName().toString();
    }


    @Override
    public String writeTableName(Element entity) {

        if (entity.getAnnotation(Table.class)!=null){
            String nameValue = entity.getAnnotation(Table.class).name();
            if (nameValue.length()>0){
                return nameValue;
            }else {
                return entity.getSimpleName().toString();
            }
        }

        return entity.getSimpleName().toString();
    }

}
