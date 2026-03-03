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
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.stzteam.features.unitprocessor.Unit")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class UnitJsonProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) return false;

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");
        boolean first = true;

        for (Element element : roundEnv.getElementsAnnotatedWith(Unit.class)) {
            Unit unitAnnotation = element.getAnnotation(Unit.class);
            String unitType = unitAnnotation.value();
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

            if (!first) jsonBuilder.append(",\n");
            jsonBuilder.append("  \"").append(jsonKey).append("\": \"").append(unitType).append("\"");
            first = false;
        }

        jsonBuilder.append("\n}\n");

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