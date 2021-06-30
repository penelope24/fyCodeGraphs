package fy.cfg.visitor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.*;
import fy.cfg.parse.TypeSolver;
import fy.structures.DFVarNode;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class VarVisitor {

    private HashMap<String, Set<String>> pkg2types;
    private List<String> allImports;
    private String pkgInfo;
    private Set<DFVarNode> allFields;
    private CompilationUnit cu;

    public VarVisitor(HashMap<String, Set<String>> pkg2types, CompilationUnit cu) {
        this.pkg2types = pkg2types;
        this.cu = cu;
        allImports = new ArrayList<>();
        pkgInfo = null;
        init();
    }

    public void init () {
        // parse imports
        cu.getImports().forEach(importDeclaration -> {
            allImports.add(importDeclaration.getNameAsString());
        });
        // parse package
        boolean is_pkg_present = cu.findFirst(PackageDeclaration.class).isPresent();
        pkgInfo = is_pkg_present ? cu.findFirst(PackageDeclaration.class).get().getNameAsString() : "";
    }

    public void analyseFieldTypes() {
        allFields = new HashSet<>();
        cu.findAll(FieldDeclaration.class).forEach(fieldDeclaration -> {
            NodeList<VariableDeclarator> variables = fieldDeclaration.getVariables();
            variables.forEach(var -> {
                analyseSingleVar(var, allFields);
            });
        });
    }

    public void analyseSingleVar(VariableDeclarator variable, Set<DFVarNode> allFields) {
        String name = variable.getNameAsString();
        Type type = variable.getType();
        String typeName = parseVarType(type);
        allFields.add(new DFVarNode(name, typeName));
    }

    public String parseVarType (Type type) {
        if (type.isUnknownType()) {
            // lambda 中用到的未知参数
            UnknownType unknownType = type.asUnknownType();
            return "unknownType";
        } else if (type.isUnionType()) {
            // catch中的联合类型
            List<String> typeStr = new ArrayList<>();
            UnionType unionType = type.asUnionType();
            NodeList<ReferenceType> elements = unionType.getElements();
            for (Type referenceType : elements) {
                typeStr.add(parseVarType(referenceType));
            }
            return StringUtils.join(typeStr, ";");
        } else if (type.isVarType()) {
            VarType varType = type.asVarType();
            return varType.asString();
        } else if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classOrInterfaceType = type.asClassOrInterfaceType();
            String name = classOrInterfaceType.getNameAsString();
            return TypeSolver.solveSimpleTypeName(name, allImports, pkgInfo, pkg2types);
        } else if (type.isArrayType()) {
            //array type 就是一个[]
            ArrayType arrayType = type.asArrayType();
            String s = parseVarType(arrayType.getComponentType());
            return s + "[]";
        } else if (type.isTypeParameter()) {
            //这个是泛型中用到的类型 这个目前节点没有关注
            TypeParameter typeParameter = type.asTypeParameter();
            String name = typeParameter.getNameAsString();
            return TypeSolver.solveSimpleTypeName(name, allImports, pkgInfo, pkg2types);
        } else if (type.isPrimitiveType()) {
            //jdk自带的类型
            PrimitiveType primitiveType = type.asPrimitiveType();
            return "JavaLang" + primitiveType.getType().asString();
        } else if (type.isWildcardType()) {
            WildcardType wildcardType = type.asWildcardType();
            StringBuilder sb = new StringBuilder();
            if (wildcardType.getExtendedType().isPresent()) {
                sb.append(parseVarType(wildcardType.getExtendedType().get()));
                sb.append(";");
            }
            if (wildcardType.getSuperType().isPresent()) {
                sb.append(parseVarType(wildcardType.getSuperType().get()));
            }
            return sb.toString();
        } else if (type.isVoidType()) {
            VoidType voidType = type.asVoidType();
            return voidType.asString();
        } else if (type.isIntersectionType()) {
            IntersectionType intersectionType = type.asIntersectionType();
            List<String> typeStr = new ArrayList<>();
            for (Type t : intersectionType.getElements()) {
                typeStr.add(parseVarType(t));
            }
            return StringUtils.join(typeStr, ";");
        }
        return "";
    }


    public Set<String> getCurrentPackageAllTypes() {
        return this.pkg2types.get(pkgInfo);
    }

    public List<String> getAllImports() {
        return allImports;
    }

    public Set<DFVarNode> getAllFields() {
        return allFields;
    }

    public String getPackageStr() {
        return pkgInfo;
    }
}
