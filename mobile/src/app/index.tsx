import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { SeoulIllustration } from '@/course/seoul-illustration';
import { useCourseStore } from '@/course/store';
import { Button, Card, Copy, Heading, Screen } from '@/course/ui';

export default function Home() {
  const reset = useCourseStore(state => state.reset);
  return (
    <Screen title={'서울의 하루를,\n내 취향대로.'} subtitle="시간과 취향에 맞춘 서울 코스">
      <SeoulIllustration />
      <Card>
        <Heading>계획은 가볍게, 하루는 알차게</Heading>
        <Copy>안국역·경복궁역 일대에서 갈 만한 장소와 방문 순서를 찾아보세요.</Copy>
      </Card>
      <View style={styles.action}>
        <Button title="나만의 코스 만들기" arrow onPress={() => { reset(); router.navigate('/survey'); }} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  action: { marginTop: 'auto', paddingTop: 10 },
});
