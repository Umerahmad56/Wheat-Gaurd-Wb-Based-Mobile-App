from django.core.management.base import BaseCommand
from api.models import DiseaseKnowledgeBase

class Command(BaseCommand):
    help = 'Seeds the database with wheat rust disease and pesticide knowledge base.'

    def handle(self, *args, **options):
        diseases = [
            {
                "name": "Yellow Rust",
                "name_ur": "پیلا زنگ (Yellow Rust)",
                "pesticide_name": "Propiconazole (Tilt 250 EC)",
                "pesticide_name_ur": "پروپیکونازول (ٹلٹ 250 ای سی)",
                "dosage": "200ml per acre",
                "dosage_ur": "200 ملی لیٹر فی ایکڑ",
                "precautions": "Use protective gear. Avoid spraying in high wind. Ensure uniform coverage.",
                "precautions_ur": "حفاظتی سامان استعمال کریں۔ تیز ہوا میں سپرے کرنے سے گریز کریں۔ یکساں چھڑکاو کو یقینی بنائیں۔",
                "description": "Characterized by yellow, stripe-like pustules on the leaf surface. Affects early growth stages under cool and moist conditions.",
                "description_ur": "پتے کی سطح پر پیلے رنگ کی دھاری دار نشانیاں بنتی ہیں۔ یہ بیماری ٹھنڈے اور مرطوب موسم میں ابتدائی نشوونما کو متاثر کرتی ہے۔"
            },
            {
                "name": "Brown Rust",
                "name_ur": "بھورا زنگ (Brown/Leaf Rust)",
                "pesticide_name": "Tebuconazole (Folicur 250 EW)",
                "pesticide_name_ur": "ٹیبوکونازول (فولیکور 250 ای ڈبلیو)",
                "dosage": "250ml per acre",
                "dosage_ur": "250 ملی لیٹر فی ایکڑ",
                "precautions": "Avoid contact with eyes and skin. Spray during cool hours of the day (morning/evening).",
                "precautions_ur": "آنکھوں اور جلد کے ساتھ رابطے سے بچیں۔ دن کے ٹھنڈے اوقات (صبح یا شام) میں سپرے کریں۔",
                "description": "Oval-shaped, orange-brown pustules scattered randomly across the leaf blade. Favored by mild temperatures.",
                "description_ur": "پتے پر بے ترتیب پھیلے ہوئے بیضوی شکل کے نارنجی بھورے رنگ کے پسٹول بنتے ہیں۔ یہ معتدل درجہ حرارت میں پھیلتی ہے۔"
            },
            {
                "name": "Black Rust",
                "name_ur": "کالا زنگ (Black/Stem Rust)",
                "pesticide_name": "Azoxystrobin + Difenoconazole (Amistar Top)",
                "pesticide_name_ur": "ازوکسیسٹروبن + ڈائی فینوکونازول (امیسٹار ٹاپ)",
                "dosage": "200ml per acre",
                "dosage_ur": "200 ملی لیٹر فی ایکڑ",
                "precautions": "Harmful to aquatic life. Wash hands and face thoroughly with soap after application.",
                "precautions_ur": "آبی حیات کے لیے نقصان دہ ہے۔ سپرے کے بعد ہاتھ اور منہ صابن سے اچھی طرح دھوئیں۔",
                "description": "Elongated, dark brown to black pustules on both leaves and stems. Can cause severe crop lodging.",
                "description_ur": "پتے اور تنے دونوں پر لمبے، گہرے بھورے سے کالے رنگ کے نشانات بنتے ہیں۔ یہ فصل کو شدید نقصان پہنچا سکتی ہے۔"
            },
            {
                "name": "Healthy",
                "name_ur": "تندرست پتا (Healthy)",
                "pesticide_name": "No Fungicide Needed",
                "pesticide_name_ur": "کسی فنگسائڈ کی ضرورت نہیں ہے",
                "dosage": "N/A",
                "dosage_ur": "لاگو نہیں ہوتا",
                "precautions": "Maintain regular crop monitoring. Follow standard irrigation and fertilization schedules.",
                "precautions_ur": "فصل کی باقاعدگی سے نگرانی جاری رکھیں۔ پانی اور کھاد کے معیاری شیڈول پر عمل کریں۔",
                "description": "The leaf is clean, vibrant green, and free from any rust pustules or signs of infection.",
                "description_ur": "پتا بالکل صاف، چمکدار سبز اور زنگ کے نشانات یا انفیکشن کی علامات سے پاک ہے۔"
            }
        ]

        for d in diseases:
            obj, created = DiseaseKnowledgeBase.objects.update_or_create(
                name=d["name"],
                defaults=d
            )
            status_str = "Created" if created else "Updated"
            self.stdout.write(self.style.SUCCESS(f"{status_str} entry for {d['name']}"))
            
        self.stdout.write(self.style.SUCCESS("Database seeded successfully!"))
