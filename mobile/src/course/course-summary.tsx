import { Pressable, StyleSheet, Text, View } from 'react-native';

import { startPoints, themes, transportLabels, type Course, type RecommendRequest } from './contracts';
import { durationLabel, minutes, visitDateLabel } from './schedule';
import { Card, colors, Copy, Heading } from './ui';

export function courseTitle(course: Course, index: number) {
  const categories = [...new Set(course.places.map(place => themes[place.theme]))];
  return ['코스 ' + String.fromCharCode(65 + index), categories.join(' · ')].filter(Boolean).join('  ');
}

export function ConditionSummary({ request, onEdit }: { request: RecommendRequest; onEdit?: () => void }) {
  const duration = durationLabel(minutes(request.endTime) - minutes(request.startTime));
  const origin = startPoints.find(point => point.id === request.startPointId);

  return (
    <Card style={styles.card}>
      <View style={styles.row}>
        <View style={styles.summary}>
          <Heading>{visitDateLabel(request.date)}</Heading>
          <Copy>{request.startTime} · {duration}</Copy>
          <Copy>{[origin?.name, transportLabels[request.transport], ...request.themes.map(theme => themes[theme])]
            .filter(Boolean).join(' · ')}</Copy>
        </View>
        {onEdit && <Pressable accessibilityRole="button" onPress={onEdit} style={styles.edit}>
          <Text style={styles.editText}>조건 수정</Text>
        </Pressable>}
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: '#eaf1eb' },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  summary: { flex: 1, gap: 3 },
  edit: { minHeight: 44, justifyContent: 'center', paddingHorizontal: 4 },
  editText: { color: colors.primary, fontSize: 13, fontWeight: '700' },
});
