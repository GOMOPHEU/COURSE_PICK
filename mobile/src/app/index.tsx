import { router } from 'expo-router';

import { useCourseStore } from '@/course/store';
import { Button, Screen } from '@/course/ui';

export default function Home() {
  const reset = useCourseStore(state => state.reset);
  return (
    <Screen title="코스픽" subtitle="안국역 일대에서 취향과 방문 시간에 맞는 나들이 코스를 찾아보세요. 회원가입 없이 시작할 수 있어요.">
      <Button title="나만의 코스 만들기" onPress={() => { reset(); router.navigate('/survey'); }} />
    </Screen>
  );
}
