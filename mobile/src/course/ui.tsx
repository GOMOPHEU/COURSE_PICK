import type { ComponentRef, PropsWithChildren, Ref } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, type LayoutChangeEvent, type StyleProp, type ViewStyle } from 'react-native';

export const colors = {
  bg: '#f5f8f4', surface: '#fff', text: '#1d332a', muted: '#55675d', line: '#dde7de',
  primary: '#2c6d58', pale: '#e0efe7', error: '#9b263e', sand: '#faf0d6', amber: '#73521d',
};

export function Screen({ title, subtitle, children, scrollRef, style, centered = false }: PropsWithChildren<{
  title?: string; subtitle?: string; scrollRef?: Ref<ComponentRef<typeof ScrollView>>;
  style?: StyleProp<ViewStyle>; centered?: boolean;
}>) {
  return (
    <ScrollView ref={scrollRef} style={styles.page} contentContainerStyle={[styles.body, style]} keyboardShouldPersistTaps="handled">
      {title && <Text style={[styles.title, centered && styles.centered]} accessibilityRole="header">{title}</Text>}
      {subtitle && <Text style={[styles.caption, centered && styles.centered]}>{subtitle}</Text>}
      {children}
    </ScrollView>
  );
}

export function Card({ children, style }: PropsWithChildren<{ style?: StyleProp<ViewStyle> }>) {
  return <View style={[styles.card, style]}>{children}</View>;
}

export function Heading({ children }: PropsWithChildren) {
  return <Text style={styles.heading} accessibilityRole="header">{children}</Text>;
}

export function Copy({ children }: PropsWithChildren) {
  return <Text style={styles.caption}>{children}</Text>;
}

export function ErrorText({ message }: { message: string }) {
  return <Text accessibilityRole="alert" style={styles.error}>{message}</Text>;
}

export function Button({ title, onPress, secondary = false, arrow = false }: {
  title: string; onPress: () => void; secondary?: boolean; arrow?: boolean;
}) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [
      styles.button, secondary && styles.secondary, pressed && styles.pressed,
    ]}>
      <Text style={[styles.buttonText, secondary && styles.selectedText]}>{title}</Text>
      {arrow && <Text accessible={false} style={styles.buttonArrow}>→</Text>}
    </Pressable>
  );
}

export function FitBadge({ fit }: { fit: 'relaxed' | 'tight' }) {
  const tight = fit === 'tight';
  return <Text style={[styles.badge, tight && styles.tight]}>{tight ? '빡빡함' : '여유'}</Text>;
}

export function ChoiceGroup<T extends string>({
  title, options, selected, onChange, error, onLayout, columns,
}: {
  title: string;
  options: readonly { value: T; label: string }[];
  selected: readonly T[];
  onChange: (value: T) => void;
  error?: string;
  onLayout?: (event: LayoutChangeEvent) => void;
  columns?: 2 | 3;
}) {
  return (
    <View style={styles.field} onLayout={onLayout}>
      <Text accessibilityRole="header" style={[styles.heading, error && styles.invalidLabel]}>{title}</Text>
      <View style={[styles.choices, columns && styles.grid]}>
        {options.map(({ value, label }) => {
          const active = selected.includes(value);
          return (
            <Pressable key={value} accessibilityRole="button" accessibilityState={{ selected: active }}
              onPress={() => onChange(value)}
              style={[styles.choice, columns === 2 && styles.half, columns === 3 && styles.third, active && styles.selected]}>
              <Text style={[styles.choiceText, active && styles.selectedText]}>{label}</Text>
            </Pressable>
          );
        })}
      </View>
      {error && <ErrorText message={error} />}
    </View>
  );
}

export function SelectionField({ label, value, onPress, error, onLayout, expanded, style }: {
  label: string; value: string; onPress: () => void; error?: string; onLayout?: (event: LayoutChangeEvent) => void;
  expanded?: boolean; style?: StyleProp<ViewStyle>;
}) {
  return (
    <View style={[styles.field, style]} onLayout={onLayout}>
      <Text accessibilityRole="header" style={[styles.heading, error && styles.invalidLabel]}>{label}</Text>
      <Pressable accessibilityRole="button" accessibilityLabel={label + ' ' + value + ' 선택'}
        accessibilityState={expanded === undefined ? undefined : { expanded }}
        onPress={onPress} style={[styles.selectionField, error && styles.invalidBorder]}>
        <Text style={styles.selectionValue}>{value}</Text>
        <Text accessible={false} style={styles.selectedText}>{expanded ? '⌃' : '›'}</Text>
      </Pressable>
      {error && <ErrorText message={error} />}
    </View>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.bg },
  body: { padding: 18, gap: 14, paddingBottom: 28, width: '100%', maxWidth: 560, alignSelf: 'center', flexGrow: 1 },
  title: { color: colors.text, fontSize: 25, fontWeight: '800', letterSpacing: -0.7 },
  centered: { textAlign: 'center' },
  heading: { color: colors.text, fontSize: 16, fontWeight: '700' },
  caption: { color: colors.muted, fontSize: 14, lineHeight: 21 },
  card: { backgroundColor: colors.surface, padding: 14, borderColor: colors.line, borderWidth: 1, borderRadius: 13, gap: 6 },
  field: { gap: 6, minWidth: 0 },
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  grid: { justifyContent: 'space-between', columnGap: 0 },
  half: { width: '49%' },
  third: { width: '32%' },
  choice: { backgroundColor: colors.surface, paddingHorizontal: 8, paddingVertical: 8, minHeight: 44,
    borderRadius: 9, borderWidth: 1, borderColor: colors.line, alignItems: 'center', justifyContent: 'center' },
  selected: { backgroundColor: colors.pale, borderColor: colors.primary },
  choiceText: { color: colors.text, fontWeight: '600' },
  selectionValue: { color: colors.text, fontWeight: '600', flexShrink: 1 },
  selectedText: { color: colors.primary },
  selectionField: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, borderRadius: 9,
    minHeight: 46, paddingHorizontal: 12, flexDirection: 'row', gap: 8, alignItems: 'center', justifyContent: 'space-between' },
  button: { backgroundColor: colors.primary, minHeight: 48, paddingVertical: 12, paddingHorizontal: 34, alignItems: 'center',
    justifyContent: 'center', borderRadius: 11 },
  buttonText: { color: colors.surface, fontSize: 16, fontWeight: '700' },
  buttonArrow: { position: 'absolute', right: 14, color: colors.surface, fontSize: 17 },
  secondary: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line },
  badge: { alignSelf: 'flex-start', overflow: 'hidden', borderRadius: 12, paddingHorizontal: 8, paddingVertical: 3,
    backgroundColor: colors.pale, color: colors.primary, fontSize: 12, fontWeight: '700' },
  tight: { backgroundColor: colors.sand, color: colors.amber },
  pressed: { opacity: 0.8 },
  error: { color: colors.error, fontSize: 15, fontWeight: '600' },
  invalidLabel: { color: colors.error },
  invalidBorder: { borderColor: colors.error },
});
