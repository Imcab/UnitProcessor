package com.stzteam.features.unitprocessor;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.stzteam.mars.units.Unit")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class UnitJsonProcessor extends AbstractProcessor {

    // Candado para asegurar que el hack solo corra UNA vez y no choque
    private boolean hasGenerated = false;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty() || hasGenerated) return false;

        Messager messager = processingEnv.getMessager();
        messager.printMessage(Diagnostic.Kind.NOTE, "[MARS] Iniciando escaneo de unidades...");

        try {
            Map<String, Map<String, String>> groupedUnits = new HashMap<>();

            for (Element element : roundEnv.getElementsAnnotatedWith(Unit.class)) {
                Unit unitAnnotation = element.getAnnotation(Unit.class);
                String unitType = unitAnnotation.value();
                String groupName = unitAnnotation.group(); // Si esto falla, el catch lo atrapará
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

                groupedUnits.putIfAbsent(groupName, new HashMap<>());
                groupedUnits.get(groupName).put(jsonKey, unitType);
            }

            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\n");
            boolean firstGroup = true;

            for (Map.Entry<String, Map<String, String>> groupEntry : groupedUnits.entrySet()) {
                if (!firstGroup) jsonBuilder.append(",\n");
                jsonBuilder.append("  \"").append(groupEntry.getKey()).append("\": {\n");
                
                boolean firstItem = true;
                for (Map.Entry<String, String> itemEntry : groupEntry.getValue().entrySet()) {
                    if (!firstItem) jsonBuilder.append(",\n");
                    jsonBuilder.append("    \"").append(itemEntry.getKey()).append("\": \"").append(itemEntry.getValue()).append("\"");
                    firstItem = false;
                }
                jsonBuilder.append("\n  }");
                firstGroup = false;
            }
            jsonBuilder.append("\n}\n");

            // EL HACK BLINDADO
            FileObject dummy = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "mars_dummy_" + System.currentTimeMillis() + ".tmp");
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

            // Avisamos a la consola que fue un éxito rotundo
            messager.printMessage(Diagnostic.Kind.NOTE, "[MARS] ¡ProjectUnits.json generado exitosamente en la raíz!");
            hasGenerated = true;

        } catch (Exception e) {
            // SI ALGO FALLA, LO GRITAMOS EN ROJO EN LA CONSOLA
            messager.printMessage(Diagnostic.Kind.ERROR, "[MARS ERROR CRÍTICO] " + e.toString());
        }

        return true;
    }
}