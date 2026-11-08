package cat.narezany.margyt;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/** Recognises only the verified native plain-text cells; never changes their views. */
final class ChatBubbles {
    private ChatBubbles() {}

    private static final String TEXT = "com.ss.android.ugc.aweme.im.sdk.chat.ui.text.content.";
    private static final int BODY = 0x7f0a1bf7, ALTERNATE = 0x7f0a3ab4,
            CUSTOM_RESOURCE = 0x7f0a0a37;
    private static final ConcurrentHashMap<Class<?>, Access> access = new ConcurrentHashMap<>();

    static final class Match {
        final View body;
        final View background;
        final boolean outgoing;

        private Match(View body, View background, boolean outgoing) {
            this.body = body;
            this.background = background;
            this.outgoing = outgoing;
        }
    }

    static Match find(ViewGroup list, View row) {
        if (list == null || row == null) return null;
        try {
            Method containing = cached(list.getClass()).containing;
            if (containing == null) return null;
            Object holder = containing.invoke(list, row);
            if (holder == null) return null;

            String name = holder.getClass().getName();
            boolean outgoing = (TEXT + "TextTranslateSendCell").equals(name);
            if (!outgoing && !(TEXT + "TextTranslateReceiveCell").equals(name)) return null;
            Access holderAccess = cached(holder.getClass());
            if (holderAccess.item == null || holderAccess.itemView == null
                    || holderAccess.itemView.get(holder) != row) return null;

            // PowerCell.getItem() is inherited. Do not infer metadata from child views.
            Object item = holderAccess.item.invoke(holder);
            if (item == null || !(outgoing ? "X.15FJ" : "X.15FI").equals(item.getClass().getName()))
                return null;
            Method configMethod = cached(item.getClass()).config;
            if (configMethod == null) return null;
            Object config = configMethod.invoke(item);
            if (config == null || !"X.0QTr".equals(config.getClass().getName())) return null;
            Field styleField = cached(config.getClass()).style;
            if (styleField == null) return null;
            Object style = styleField.get(config);
            if (style == null) return null;
            String styleName = style.getClass().getName();
            // Custom images have metadata before their asynchronous load adds a view tag.
            if ("X.15FC".equals(styleName)) return null;
            if (!"X.13Z9".equals(styleName) && !"X.13ZA".equals(styleName)
                    && !"X.13ZB".equals(styleName)) return null;

            View body = row.findViewById(BODY);
            View alternate = row.findViewById(ALTERNATE);
            if (!(body instanceof ViewGroup) || body.getParent() != row
                    || (alternate != null && alternate.getParent() != row)) return null;
            if (body.getTag(CUSTOM_RESOURCE) != null
                    || (alternate != null && alternate.getTag(CUSTOM_RESOURCE) != null)) return null;

            // TextTranslate*Assem uses 15Kc.LIZ(root, false) for the actual bubble.
            // Its true branch selects kwn, the alternate background, not message direction.
            Selector selector = holderAccess.selector;
            View background = body;
            if (selector != null) {
                Object selected = selector.method.invoke(selector.owner, row, false);
                if (!(selected instanceof View) || (selected != body && selected != alternate)) return null;
                background = (View) selected;
            }
            return new Match(body, background, outgoing);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Access cached(Class<?> type) {
        Access found = access.get(type);
        if (found != null) return found;
        Access made = new Access(type);
        Access existing = access.putIfAbsent(type, made);
        return existing == null ? made : existing;
    }

    /** Cache missing members too, without retaining a list, holder, item or message. */
    private static final class Access {
        final Method containing, item, config;
        final Field itemView, style;
        final Selector selector;

        Access(Class<?> type) {
            containing = method(type, "findContainingViewHolder", View.class);
            item = method(type, "getItem");
            config = method(type, "LJI");
            itemView = field(type, "itemView");
            style = field(type, "LIZJ");
            String name = type.getName();
            selector = ((TEXT + "TextTranslateSendCell").equals(name)
                    || (TEXT + "TextTranslateReceiveCell").equals(name))
                    ? Selector.load(type.getClassLoader()) : null;
        }
    }

    private static final class Selector {
        final Object owner;
        final Method method;

        Selector(Object owner, Method method) {
            this.owner = owner;
            this.method = method;
        }

        static Selector load(ClassLoader loader) {
            try {
                Class<?> type = Class.forName("X.15Kc", false, loader);
                Object owner = type.getField("LIZ").get(null);
                Method method = type.getMethod("LIZ", View.class, boolean.class);
                return owner == null ? null : new Selector(owner, method);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Verified plain metadata still permits the conservative f5z fallback.
                return null;
            }
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException | SecurityException ignored) {
            return null;
        }
    }

    private static Field field(Class<?> type, String name) {
        try {
            return type.getField(name);
        } catch (NoSuchFieldException | SecurityException ignored) {
            return null;
        }
    }
}
