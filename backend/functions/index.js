const {onSchedule} = require('firebase-functions/v2/scheduler');
const {initializeApp} = require('firebase-admin/app');
const {getMessaging} = require('firebase-admin/messaging');
initializeApp();
const copy = {
  en: 'Bring your next idea to life. Edit a video or polish a photo in Lumen Studio.',
  ur: 'اپنے اگلے خیال کو حقیقت بنائیں۔ Lumen Studio میں ویڈیو ایڈٹ کریں یا تصویر نکھاریں۔',
  hi: 'अपने अगले विचार को साकार करें। Lumen Studio में वीडियो संपादित करें या फ़ोटो निखारें।',
  zh: '让下一个创意成为现实。在 Lumen Studio 中编辑视频或美化照片。'
};
exports.editingReminder = onSchedule({schedule:'0 */3 * * *',timeZone:'Asia/Karachi',retryCount:0}, async () => {
  await Promise.all(Object.entries(copy).map(([language,body]) => getMessaging().send({
    topic:`editing-reminders_${language}`,
    data:{title:'Lumen Studio',body,language},
    android:{priority:'normal',ttl:10800000,collapseKey:'editing-reminder'}
  })));
});
