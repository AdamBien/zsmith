package airhacks.zsmith.tools.entity;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/// On a record tool: the tool description the LLM sees.
/// On a record component: the parameter description in the input schema.
@Retention(RUNTIME)
@Target({TYPE, RECORD_COMPONENT})
public @interface Describe {

    String value();
}
