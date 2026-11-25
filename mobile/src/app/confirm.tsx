import { router } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { startPoints, themes, transportLabels } from '@/course/contracts';
import { durationLabel, minutes, visitDateLabel } from '@/course/schedule';
import { useCourseStore } from '@/course/store';
import { Button, Card, colors, Screen } from '@/course/ui';

const companions = { solo: '혼자', friends: '친구', family: '가족', couple: '연인' };

export default function Confirm() {
  const input = useCourseStore(state => state.request);
  const duration = durationLabel(minutes(input.endTime) - minutes(input.startTime));
  const rows = [
    ['출발 지점', startPoints.find(point => point.id === input.startPointId)?.name ?? '미선택'],
    ['방문 날짜', visitDateLabel(input.date)],
    ['시작 / 이용 시간', input.startTime + ' / ' + duration],
    ['이동 수단', transportLabels[input.transport]],
    ['선호 테마', input.themes.map(theme => themes[theme]).join(' · ')],
    ['동행 유형', input.companion ? companions[input.companion] : '미선택'],
    ['동행 인원', input.partySize ? (input.partySize >= 5 ? '5명 이상' : input.partySize + '명') : '미선택'],
    ['이동 선호도', input.mobility ? '도보 최소화' : '미정'],
    ['연령대', input.ageGroup ? (input.ageGroup === '50plus' ? '50대 이상' : input.ageGroup.slice(0, 2) + '대') : '미선택'],
  ];

  return (
    <Screen title="이 조건으로 추천받을까요?">
      <Card style={styles.card}>
        {rows.map(([label, value], index) => (
          <View key={label} style={[styles.row, index > 0 && styles.divider]}>
            <Text style={styles.label}>{label}</Text>
            <Text style={styles.value}>{value}</Text>
          </View>
        ))}
      </Card>
      <View style={styles.actions}>
        <Button title="이 조건으로 추천받기" arrow onPress={() => router.navigate('/recommend')} />
        <Button title="조건 수정" secondary onPress={() => router.dismissTo('/survey')} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { paddingVertical: 2, gap: 0 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12, paddingVertical: 10 },
  divider: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  label: { color: colors.muted, fontSize: 13, flexShrink: 1 },
  value: { color: colors.text, fontSize: 14, fontWeight: '600', textAlign: 'right', maxWidth: '60%' },
  actions: { marginTop: 'auto', gap: 8 },
});
