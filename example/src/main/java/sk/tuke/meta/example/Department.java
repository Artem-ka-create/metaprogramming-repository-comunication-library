package sk.tuke.meta.example;


import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "dep")
public class Department implements IDepartment{
    @Id
    @Column(name = "id")
    private long pk;

    @Column(name = "name",nullable = false, unique = true)
    private String name;

    @Column(name = "code",nullable = false, unique = true)
    private String code;

    public Department() {
    }

    public Department(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void setName(String name) {

        this.name=name;
    }

    @Override
    public String getCode() {
        return null;
    }

    @Override
    public void setCode(String code) {
        this.code=code;
    }
    @Override
    public String toString() {
        return "Department{" +
                "pk=" + pk +
                ", name='" + name + '\'' +
                ", code='" + code + '\'' +
                '}';
    }


}
