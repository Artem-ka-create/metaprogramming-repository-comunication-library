package sk.tuke.meta.sav.processor.generation;

import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;

public interface Generator {

    boolean isId(VariableElement field);

    boolean isRelation(VariableElement field);

    boolean isInteger(VariableElement field);

    boolean isString(VariableElement field) throws ClassNotFoundException;

    Object  getRelationClass(VariableElement field) throws ClassNotFoundException;



    String writeColumnConfig(VariableElement field,String opperation);

    String writeColumnName(VariableElement field) throws ClassNotFoundException;

    String writeTableName(Element entity) throws ClassNotFoundException;

}
