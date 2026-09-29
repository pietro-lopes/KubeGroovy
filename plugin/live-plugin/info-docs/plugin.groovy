// depends-on-plugin com.intellij.java
// depends-on-plugin org.intellij.groovy
//
// @Info documentation (LivePlugin script).
//
// SCOPE
//   Only .groovy use-sites under kubegroovy/{startup,client,server}_scripts/**.
//
// WHAT IT DOES
//   Renders dev.uncandango.kubegroovy.typings.Info annotation values as IDE
//   documentation (completion popup, quick-doc, hover) for annotated methods,
//   fields, and classes. The mod documents its script-facing API through
//   @Info (readable at runtime by document generation) instead of /** */
//   comments, so without this provider those members show a bare signature
//   and no docs. A DocumentationProvider extension covers every surface at
//   once: LookupElementBuilder has no per-row documentation API, so completion
//   rows cannot carry this text directly.
//   Members without a usable @Info return null and fall through to the
//   platform's default providers, so unrelated code is untouched.
//
// LOOKUP / COST
//   Documentation renders on user action (popup, Ctrl+Q, hover), never while
//   typing. The annotation is read by qualified name straight from PSI, so no
//   class resolution or index access happens and this works in dumb mode.
//
// RUNNING IT
//   compile.bat checks it compiles; install.bat copies the script to
//   LivePlugin; reload it from the LivePlugin tool window.

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralValue
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner

class InfoParamDoc {
    String name
    String description
}

class InfoDocSupport {
    // Check the use-site, never the declaration being resolved. Completion copies
    // retain their original file; scratch files and unknown contexts fail closed.
    static String sideForFile(PsiFile file) {
        if (!(file instanceof GroovyFile)) return null
        PsiFile original = file.getOriginalFile()
        if (!(original instanceof GroovyFile)) return null
        String path = original.getVirtualFile()?.getPath()?.replace('\\', '/')
        if (path == null || !path.endsWith('.groovy')) return null
        def match = (path =~ /(?:^|\/)kubegroovy\/(startup|client|server)_scripts\/.+\.groovy$/)
        return match.find() ? match.group(1) : null
    }

    static final Logger LOG = Logger.getInstance("KubeGroovyInfoDocs")
    static final String INFO_ANNOTATION = "dev.uncandango.kubegroovy.typings.Info"
    static final String PARAM_ANNOTATION = "dev.uncandango.kubegroovy.typings.Param"

    static String docFor(PsiElement element) {
        try {
            if (!(element instanceof PsiModifierListOwner)) return null
            PsiModifierListOwner owner = (PsiModifierListOwner) element
            if (!(owner instanceof PsiMethod) && !(owner instanceof PsiField) && !(owner instanceof PsiClass)) return null

            def modifierList = null
            try {
                modifierList = owner.getModifierList()
            } catch (Throwable ignored) {
                return null
            }
            if (modifierList == null) return null
            PsiAnnotation info = null
            try {
                info = modifierList.findAnnotation(INFO_ANNOTATION)
            } catch (Throwable ignored) {
                return null
            }
            if (info == null) return null

            String description = stringAttribute(info, "value")
            List<InfoParamDoc> params = paramDocs(owner, info)
            if ((description == null || description.trim().isEmpty()) && params.isEmpty()) return null

            StringBuilder doc = new StringBuilder()
            doc.append(DocumentationMarkup.DEFINITION_START).append(definition(owner)).append(DocumentationMarkup.DEFINITION_END)
            doc.append(DocumentationMarkup.CONTENT_START)
            if (description != null && !description.trim().isEmpty()) {
                doc.append("<p>").append(multiline(description)).append("</p>")
            }
            if (!params.isEmpty()) {
                doc.append("<table>")
                for (InfoParamDoc param : params) {
                    doc.append("<tr><td><code>").append(escape(param.name)).append("</code></td><td>")
                        .append(multiline(param.description)).append("</td></tr>")
                }
                doc.append("</table>")
            }
            doc.append(DocumentationMarkup.CONTENT_END)
            return doc.toString()
        } catch (Throwable ignored) {
            return null
        }
    }

    /**
     * Builds the param rows for an {@code @Info}: entries map onto method
     * parameters by order (the {@code @Param} contract), with {@code name()}
     * overriding the displayed name; stray entries with an explicit name are
     * appended.
     */
    static List<InfoParamDoc> paramDocs(PsiModifierListOwner owner, PsiAnnotation info) {
        try {
            def raw = null
            try {
                raw = info.findAttributeValue("params")
            } catch (Throwable ignored) {
                return Collections.emptyList()
            }
            if (!(raw instanceof PsiArrayInitializerMemberValue)) return Collections.emptyList()

            List<InfoParamDoc> entries = new ArrayList<InfoParamDoc>()
            for (PsiAnnotationMemberValue initializer : ((PsiArrayInitializerMemberValue) raw).getInitializers()) {
                try {
                    if (!(initializer instanceof PsiAnnotation)) continue
                    PsiAnnotation entry = (PsiAnnotation) initializer
                    if (!PARAM_ANNOTATION.equals(entry.getQualifiedName())) continue
                    String value = stringAttribute(entry, "value")
                    if (value == null || value.trim().isEmpty()) continue
                    String name = stringAttribute(entry, "name")
                    InfoParamDoc doc = new InfoParamDoc()
                    doc.name = name == null ? "" : name
                    doc.description = value
                    entries.add(doc)
                } catch (Throwable ignored2) {}
            }
            if (entries.isEmpty()) return Collections.emptyList()

            List<InfoParamDoc> docs = new ArrayList<InfoParamDoc>()
            if (owner instanceof PsiMethod) {
                def parameters = null
                try {
                    parameters = ((PsiMethod) owner).getParameterList().getParameters()
                } catch (Throwable ignored) {
                    return Collections.emptyList()
                }
                if (parameters == null) return Collections.emptyList()
                int shared = Math.min(parameters.length, entries.size())
                for (int i = 0; i < shared; i++) {
                    try {
                        String explicit = entries.get(i).name
                        InfoParamDoc doc = new InfoParamDoc()
                        doc.name = explicit == null || explicit.trim().isEmpty() ? parameters[i].getName() : explicit
                        doc.description = entries.get(i).description
                        docs.add(doc)
                    } catch (Throwable ignored2) {}
                }
                for (int i = parameters.length; i < entries.size(); i++) {
                    try {
                        if (entries.get(i).name != null && !entries.get(i).name.trim().isEmpty()) docs.add(entries.get(i))
                    } catch (Throwable ignored2) {}
                }
            } else {
                for (InfoParamDoc entry : entries) {
                    try {
                        if (entry.name != null && !entry.name.trim().isEmpty()) docs.add(entry)
                    } catch (Throwable ignored2) {}
                }
            }
            return docs
        } catch (Throwable ignored) {
            return Collections.emptyList()
        }
    }

    static String stringAttribute(PsiAnnotation annotation, String name) {
        try {
            def value = annotation.findAttributeValue(name)
            if (value instanceof PsiLiteralValue) {
                def literal = ((PsiLiteralValue) value).getValue()
                if (literal instanceof String) return (String) literal
            }
        } catch (Throwable ignored) {}
        return null
    }

    static String definition(PsiModifierListOwner owner) {
        try {
            if (owner instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) owner
                StringBuilder signature = new StringBuilder()
                signature.append(escape(String.valueOf(method.getName()))).append('(')
                def parameters = method.getParameterList().getParameters()
                for (int i = 0; i < parameters.length; i++) {
                    if (i > 0) signature.append(", ")
                    signature.append(escape(String.valueOf(parameters[i].getType().getPresentableText())))
                        .append(' ').append(escape(String.valueOf(parameters[i].getName())))
                }
                signature.append(')')
                def returnType = method.getReturnType()
                if (returnType != null) signature.append(": ").append(escape(String.valueOf(returnType.getPresentableText())))
                def containing = method.getContainingClass()
                if (containing != null && containing.getName() != null) {
                    return escape(String.valueOf(containing.getName())) + "." + signature.toString()
                }
                return signature.toString()
            }
            if (owner instanceof PsiField) {
                PsiField field = (PsiField) owner
                return escape(String.valueOf(field.getType().getPresentableText())) + " " + escape(String.valueOf(field.getName()))
            }
            PsiClass named = (PsiClass) owner
            String kind = "class "
            try {
                if (named.isInterface()) kind = "interface "
                else if (named.isEnum()) kind = "enum "
                else if (named.isRecord()) kind = "record "
                else if (named.isAnnotationType()) kind = "@interface "
            } catch (Throwable ignored) {}
            return kind + escape(String.valueOf(named.getName() == null ? "anonymous" : named.getName()))
        } catch (Throwable ignored) {
            return ""
        }
    }

    static String multiline(String text) {
        try {
            if (text == null) return ""
            return escape(text).replace("\r\n", "\n").replace('\r' as char, '\n' as char).replace("\n", "<br>")
        } catch (Throwable ignored) {
            return ""
        }
    }

    static String escape(String text) {
        try {
            if (text == null) return ""
            String escaped = StringUtil.escapeXmlEntities(text)
            return escaped == null ? "" : escaped
        } catch (Throwable ignored) {
            return text == null ? "" : text
        }
    }
}

class InfoDocumentationProvider extends AbstractDocumentationProvider implements DumbAware {
    @Override
    String generateDoc(PsiElement element, PsiElement originalElement) {
        if (InfoDocSupport.sideForFile(originalElement?.getContainingFile()) == null) return null
        return InfoDocSupport.docFor(element)
    }

    @Override
    String generateHoverDoc(PsiElement element, PsiElement originalElement) {
        if (InfoDocSupport.sideForFile(originalElement?.getContainingFile()) == null) return null
        return InfoDocSupport.docFor(element)
    }
}

// --- LivePlugin registration (unregistered automatically via pluginDisposable) ---
try {
    ExtensionPointName.create("com.intellij.documentationProvider").point
        .registerExtension(new InfoDocumentationProvider(), pluginDisposable)
    InfoDocSupport.LOG.info("[info-docs] documentation provider registered")
} catch (Throwable ignored) {}
