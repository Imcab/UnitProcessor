package com.stzteam.features.unitprocessor;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.stzteam.feature.unitprocessor.Unit")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class UnitJsonProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) return false;

        // Estructura: Grupo -> (Llave -> Unidad)
        Map<String, Map<String, String>> groupedUnits = new HashMap<>();

        // 1. Recopilamos y agrupamos la información
        for (Element element : roundEnv.getElementsAnnotatedWith(Unit.class)) {
            Unit unitAnnotation = element.getAnnotation(Unit.class);
            String unitType = unitAnnotation.value();
            String groupName = unitAnnotation.group();
            String jsonKey = "";

            if (element.getKind() == ElementKind.PARAMETER) {
                ExecutableElement method = (ExecutableElement) element.getEnclosingElement();
                String className = method.getEnclosingElement().getSimpleName().toString();
                String methodName = method.getSimpleName().toString();
                String paramName = element.getSimpleName().toString();
                jsonKey = className + "." + methodName + "(" + paramName + ")";
            } else if (element.getKind() == ElementKind.FIELD || element.getKind() == ElementKind.METHOD) {
                String className = element.getEnclosingElement().getSimpleName().toString();
                String elementName = element.getSimpleName().toString();
                jsonKey = className + "." + elementName;
            }

            // Si el grupo no existe, lo creamos
            groupedUnits.putIfAbsent(groupName, new HashMap<>());
            // Metemos la variable en su grupo correspondiente
            groupedUnits.get(groupName).put(jsonKey, unitType);
        }

        // 2. Construimos el JSON anidado
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");
        boolean firstGroup = true;

        for (Map.Entry<String, Map<String, String>> groupEntry : groupedUnits.entrySet()) {
            if (!firstGroup) jsonBuilder.append(",\n");
            
            // Nombre del grupo (Ej: "Arm")
            jsonBuilder.append("  \"").append(groupEntry.getKey()).append("\": {\n");
            
            boolean firstItem = true;
            for (Map.Entry<String, String> itemEntry : groupEntry.getValue().entrySet()) {
                if (!firstItem) jsonBuilder.append(",\n");
                
                // Llave y Valor (Ej: "Turret.limit": "Degrees")
                jsonBuilder.append("    \"").append(itemEntry.getKey()).append("\": \"").append(itemEntry.getValue()).append("\"");
                firstItem = false;
            }
            jsonBuilder.append("\n  }");
            firstGroup = false;
        }

        jsonBuilder.append("\n}\n");

        // 3. Escribimos el archivo en la raíz del proyecto (Hack MARS)
        try {
            FileObject dummy = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "mars_dummy.tmp");
            File dummyFile = new File(dummy.toUri().getPath());

            File projectRoot = dummyFile.getParentFile();
            while (projectRoot != null && !projectRoot.getName().equals("build")) {
                projectRoot = projectRoot.getParentFile();
            }

            if (projectRoot != null && projectRoot.getParentFile() != null) {
                projectRoot = projectRoot.getParentFile();
            } else {
                projectRoot = new File(".");
            }

            File jsonFile = new File(projectRoot, "ProjectUnits.json");
            
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(jsonBuilder.toString());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }
}