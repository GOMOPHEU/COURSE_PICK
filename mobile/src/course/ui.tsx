import type { ComponentRef, PropsWithChildren, Ref } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';

export const colors = {
  bg: '#f4f7fb', surface: '#fff', text: '#11223c', muted: '#4c607c', line: '#d8e2ee',
  blue: '#2358bc', pale: '#e8f0ff', error: '#9b263e',
};

export function Screen({ title, subtitle, children, scrollRef }: PropsWithChildren<{
  title: string; subtitle?: string; scrollRef?: Ref<ComponentRef<typeof ScrollView>>;
}>) {
  return (
    <ScrollView ref={scrollRef} style={styles.page} contentContainerStyle={styles.body} keyboardShouldPersistTaps="handled">
      <Text style={styles.title} accessibilityRole="header">{title}</Text>
      {subtitle && <Text style={styles.caption}>{subtitle}</Text>}
      {children}
    </ScrollView>
  );
}

export function Card({ children, onLayout }: PropsWithChildren<{ onLayout?: (event: LayoutChangeEvent) => void }>) {
  return <View style={styles.card} onLayout={onLayout}>{children}</View>;
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

export function Button({ title, onPress, secondary = false }: { title: string; onPress: () => void; secondary?: boolean }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [
      styles.button, secondary && styles.secondary, pressed && styles.pressed,
    ]}>
      <Text style={[styles.buttonText, secondary && styles.selectedText]}>{title}</Text>
    </Pressable>
  );
}

export function ChoiceGroup<T extends string>({
  title, options, selected, onChange, error, onLayout,
}: {
  title: string;
  options: readonly { value: T; label: string }[];
  selected: readonly T[];
  onChange: (value: T) => void;
  error?: string;
  onLayout?: (event: LayoutChangeEvent) => void;
}) {
  return (
    <View style={styles.field} onLayout={onLayout}>
      <Text accessibilityRole="header" style={[styles.heading, error && styles.invalidLabel]}>{title}</Text>
      <View style={styles.choices}>
        {options.map(({ value, label }) => {
          const active = selected.includes(value);
          return (
            <Pressable key={value} accessibilityRole="button" accessibilityState={{ selected: active }}
              onPress={() => onChange(value)} style={[styles.choice, active && styles.selected]}>
              <Text style={[styles.choiceText, active && styles.selectedText]}>{label}</Text>
            </Pressable>
          );
        })}
      </View>
      {error && <ErrorText message={error} />}
    </View>
  );
}

export function SelectionField({ label, value, onPress, error, onLayout }: {
  label: string; value: string; onPress: () => void; error?: string; onLayout?: (event: LayoutChangeEvent) => void;
}) {
  return (
    <View style={styles.field} onLayout={onLayout}>
      <Text accessibilityRole="header" style={[styles.heading, error && styles.invalidLabel]}>{label}</Text>
      <Pressable accessibilityRole="button" accessibilityLabel={label + ' ' + value + ' 선택'}
        onPress={onPress} style={[styles.selectionField, error && styles.invalidBorder]}>
        <Text style={styles.choiceText}>{value}</Text>
        <Text style={styles.selectedText}>선택</Text>
      </Pressable>
      {error && <ErrorText message={error} />}
    </View>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.bg },
  body: { padding: 22, gap: 16, paddingBottom: 38, width: '100%', maxWidth: 620, alignSelf: 'center' },
  title: { color: colors.text, fontSize: 31, fontWeight: '800', letterSpacing: -0.8 },
  heading: { color: colors.text, fontSize: 16, fontWeight: '700' },
  caption: { color: colors.muted, fontSize: 15, lineHeight: 23 },
  card: { backgroundColor: colors.surface, padding: 17, borderColor: colors.line, borderWidth: 1, borderRadius: 17, gap: 11 },
  field: { gap: 8 },
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  choice: { backgroundColor: colors.surface, paddingHorizontal: 13, paddingVertical: 10, minHeight: 44,
    borderRadius: 12, borderWidth: 1, borderColor: colors.line },
  selected: { backgroundColor: colors.pale, borderColor: colors.blue },
  choiceText: { color: colors.text, fontWeight: '600' },
  selectedText: { color: colors.blue },
  selectionField: { borderWidth: 1, borderColor: colors.line, borderRadius: 11,
    minHeight: 48, paddingHorizontal: 13, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  button: { backgroundColor: colors.blue, minHeight: 50, padding: 13, alignItems: 'center',
    justifyContent: 'center', borderRadius: 13 },
  buttonText: { color: colors.surface, fontSize: 16, fontWeight: '700' },
  secondary: { backgroundColor: colors.pale },
  pressed: { opacity: 0.8 },
  error: { color: colors.error, fontSize: 15, fontWeight: '600' },
  invalidLabel: { color: colors.error },
  invalidBorder: { borderColor: colors.error },
});
