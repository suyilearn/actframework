package act.view.rythm;

/*-
 * #%L
 * ACT Framework
 * %%
 * Copyright (C) 2014 - 2017 ActFramework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import act.Act;
import act.act_messages;
import act.app.ActionContext;
import act.i18n.I18n;
import act.route.Router;
import act.util.ActContext;
import org.osgl.$;
import org.osgl.Osgl;
import org.osgl.exception.NotAppliedException;
import org.osgl.util.E;
import org.rythmengine.RythmEngine;
import org.rythmengine.template.JavaTagBase;
import org.rythmengine.utils.S;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines fast tags for Act app
 */
public class Tags {

    @Inject
    private List<JavaTagBase> fastTags;

    public void register(RythmEngine engine) {
        for (JavaTagBase tag : fastTags) {
            engine.registerFastTag(tag);
        }
    }

    /**
     * Retrieve validation error message by field name
     *
     * Usage: `@_error("foo.name")` where`foo.name` is the field name
     */
    public static class ValidationError extends JavaTagBase {
        @Override
        public String __getName() {
            return "_error";
        }

        @Override
        protected void call(__ParameterList params, __Body body) {
            int paramSize = params.size();
            E.illegalArgumentIf(paramSize < 1);
            String field = params.get(0).value.toString();
            ConstraintViolation violation = ActContext.Base.currentContext().violation(field);
            if (null != violation) {
                p(violation.getMessage());
            }
        }
    }

    /**
     * Retrieve act framework defined i18n messages
     *
     * Usage: `@actMsg("msg-id")`
     */
    public static class ActMessage extends JavaTagBase {
        @Override
        public String __getName() {
            return "actMsg";
        }

        @Override
        protected void call(__ParameterList params, __Body body) {
            int paramSize = params.size();
            E.illegalArgumentIf(paramSize < 1);
            String msg = params.get(0).value.toString();
            Object[] args;
            if (paramSize > 1) {
                args = new Object[paramSize - 1];
                for (int i = 1; i < paramSize; ++i) {
                    args[i - 1] = params.get(i).value;
                }
            } else {
                args = new Object[0];
            }
            p(I18n.i18n(act_messages.class, msg, args));
        }
    }

    /**
     * Retrieve reverse routing URL path
     *
     * Usage: `@fullUrl()`
     */
    public static class ReverseRouting extends JavaTagBase {

        private boolean fullUrl = false;

        public ReverseRouting() {
        }

        protected ReverseRouting(boolean fullUrl) {
            this.fullUrl = fullUrl;
        }

        @Override
        public String __getName() {
            return "url";
        }

        // See https://github.com/actframework/actframework/issues/108#infer_reference
        private static $.Func0<String> INFER_REFERENCE_PROVIDER = new $.Func0<String>() {
            @Override
            public String apply() throws NotAppliedException, Osgl.Break {
                ActContext context = ActContext.Base.currentContext();
                E.illegalStateIf(null == context, "Cannot get full action path reference outside of act context");
                /*
                 * try to determine if the template is free or bounded template
                 */
                if (context.templatePathIsImplicit()) {
                    return context.methodPath();
                } else {
                    String path = context.templatePath();
                    path = org.osgl.util.S.beforeLast(path, ".");
                    if (!isTemplateBounded(path, context.methodPath())) {
                        return context.methodPath();
                    }
                    return path.replace('/', '.');
                }
            }
        };

        private static boolean isTemplateBounded(String templatePath, String methodPath) {
            int pos = templatePath.indexOf('/');
            if (pos < 0) {
                // must be a free template without package hierarchies
                return false;
            }
            int pos2 = methodPath.indexOf('.');
            if (pos2 != pos) {
                // must be a free template as the first package path doesn't match
                return false;
            }
            String templatePathStartPkg = templatePath.substring(0, pos);
            String methodPathStartPkg = methodPath.substring(0, pos2);
            return S.eq(templatePathStartPkg, methodPathStartPkg);
        }

        // see https://github.com/actframework/actframework/issues/108
        private String inferFullPath(String actionPath) {
            E.illegalArgumentIf(S.empty(actionPath), "action path expected");
            if (actionPath.contains("/") || (!actionPath.contains(".") && !actionPath.contains("("))) {
                // this is a URL path, not action path
                return actionPath;
            }
            if (actionPath.contains("(")) {
                actionPath = org.osgl.util.S.beforeFirst(actionPath, "(");
            }
            return Router.inferFullActionPath(actionPath, INFER_REFERENCE_PROVIDER);
        }

        @Override
        protected void call(__ParameterList parameterList, __Body body) {
            Object o = parameterList.getByName("value");
            if (null == o) {
                o = parameterList.getDefault();
            }
            String value = inferFullPath(o.toString());

            boolean fullUrl = this.fullUrl;
            o = parameterList.getByName("fullUrl");
            if (null != o) {
                fullUrl = (Boolean) o;
            }

            ActionContext context = ActionContext.current();
            Router router = null == context ? Act.app().router() : context.router();

            if (value.contains("/") || !value.contains(".")) {
                int n = parameterList.size();
                Object[] args = new Object[n - 1];
                for (int i = 0; i < n - 1; ++i) {
                    args[i] = parameterList.getByPosition(i + 1);
                }
                if (fullUrl) {
                    p(router._fullUrl(value, args));
                } else {
                    p(String.format(value, args));
                }
            } else {
                // value is an action path, need reverse route
                Map<String, Object> args = new HashMap<>();
                for (__Parameter param : parameterList) {
                    String name = param.name;
                    if (S.empty(name) || "value".equals(name) || "fullUrl".equals(name)) {
                        continue;
                    }
                    args.put(param.name, param.value);
                }

                p(router.reverseRoute(value, args, fullUrl));
            }
        }
    }

    public static class FullUrl extends ReverseRouting {
        @Override
        public String __getName() {
            return "fullUrl";
        }

        public FullUrl() {
            super(true);
        }
    }

}
