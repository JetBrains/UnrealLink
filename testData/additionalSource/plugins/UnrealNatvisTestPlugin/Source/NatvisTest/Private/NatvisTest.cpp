// Copyright Epic Games, Inc. All Rights Reserved.

#include "NatvisTest.h"


#include "GameplayTagContainer.h"
#include "Containers/UnrealString.h"
#include "Containers/StringView.h"
#include "Containers/Union.h"
#include "Misc/InlineValue.h"
#include "Misc/StringBuilder.h"
#include "Misc/TVariant.h"
#include "Stats/StatsData.h"

__declspec(noinline) void nop()
{
}

// ReSharper disable CppDeclaratorNeverUsed

void TestFString()
{
	FString empty;

	FString valid = TEXT("Valid string for view");
	// TODO: check strings with non-ascii symbols

	nop(); // BREAK HERE
}

void TestFStringView()
{
	TStringView<ANSICHAR> empty_ansi;
	TStringView<WIDECHAR> empty_wide;

	TStringView<ANSICHAR> ansi("Valid ansi string for view");
	TStringView<WIDECHAR> wide(L"Valid unicode string for view");

	nop(); // BREAK HERE
}

void TestFStringBuilder()
{
	TStringBuilder<42> empty;
	TStringBuilder<42> ansi;
	ansi.Append(TEXT("String builder appended string"));

	nop(); // BREAK HERE
}

void TestFGuid()
{
	FGuid guid = FGuid::NewGuid();

	nop(); // BREAK HERE
}

void TestFText()
{
	FText empty;
	FText from_text = FText::FromString(TEXT("Some text"));
	// TODO: other FText types are non-trivial to create

	nop(); // BREAK HERE
}

void TestFName()
{
	FName empty;
	FName from_text(TEXT("Some name"));

	FMinimalName minimal_name(NAME_Rotation);

	nop(); // BREAK HERE
}

void TestFStat()
{
	const FStatNameAndInfo& stat = FStatConstants::AdvanceFrame;

	// TODO: stat message and allocation info

	nop(); // BREAK HERE
}

void TestThreadSafePrimitives()
{
	FThreadSafeCounter counter(42);
	FThreadSafeCounter64 counter64(42);
	FThreadSafeBool flag_true(true);
	FThreadSafeBool flag_false(false);

	nop(); // BREAK HERE
}

void TestFTimespan()
{
	FTimespan span(1, 30, 8);

	nop(); // BREAK HERE
}

void TestFVector()
{
	FVector v(1, 2, 3);
	FVector4 v4(v, 4);
	FVector_NetQuantize v_net(v);
	// NOTE: I do not understand why there are separate natvis entries for *_NetQuantize types
	//	   But no for FVector

	nop(); // BREAK HERE
}

void TestTEnumAsByte()
{
	TEnumAsByte<EAxisOption::Type> axis_type = EAxisOption::Z;

	nop(); // BREAK HERE
}

struct Dummy
{
	int x;
	float y;
	double z;
};

uint32 GetTypeHash(const Dummy& v)
{
	return HashCombine(GetTypeHash(v.x), HashCombine(GetTypeHash(v.y), GetTypeHash(v.z)));
}

bool operator==(const Dummy& lhs, const Dummy& rhs)
{
	return lhs.x == rhs.x && lhs.y == rhs.y && lhs.z == rhs.z;
}

struct DummyBig
{
	int value;
	int pad[255];
};

static_assert(sizeof(DummyBig) > 64, "");

void TestTArray()
{
	TArray<int> empty;
	TArray<int> int_array = {1, 2, 3};
	TArray<Dummy> dummy_array = {{1, 2, 3}, {4, 5, 3}};
	TArray<FString> string_array = {TEXT("A"), TEXT("B"), TEXT("C")};
	TArray<FName> name_array = {TEXT("A"), TEXT("B"), TEXT("C")};
	TArray<unsigned char> byte_array = {'a', 'b', 'c'};
	TArray<wchar_t> wchar_array = {L'a', L'b', L'c'};

	TArrayView<FString> string_array_view = string_array;

	TSparseArray<Dummy*> sparse_array;
	sparse_array.Add(new Dummy{1, 2, 3});
	sparse_array.Add(new Dummy{4, 5, 6});

	TBitArray<> bit_array{true, 32};

	nop(); // BREAK HERE
}

void TestTPair()
{
	TPair<int, float> simple_pair{1, 2.5};
	TPair<FName, FString> complex_pair{TEXT("KEY"), TEXT("Value")};

	nop(); // BREAK HERE
}

void TestSmartPointers()
{
	TSharedPtr<Dummy> null_ptr;
	TSharedPtr<Dummy> sp(new Dummy{1, 2, 3});
	//TSharedRef<Dummy> empty_ref;
	TSharedRef<Dummy> sref{new Dummy{4, 5, 6}};
	TWeakPtr<Dummy> null_wp;
	TWeakPtr<Dummy> wp{sp};
	TInlineValue<Dummy> inline_small{Dummy{1, 2, 3}};
	TInlineValue<DummyBig> inline_big{DummyBig{5, {0}}};

	nop(); // BREAK HERE
}

enum E
{
	E0,
	E1,
	E2,
};

enum class EC
{
	E0,
	E1,
	E2,
};


void TestTMap()
{
	TMap<int, int> empty;
	TMap<int, float> simple_map{{1, 2}, {3, 4}, {5, 6}};
	TMap<FName, FString> complex_map{{TEXT("K1"), TEXT("V1")}, {TEXT("K2"), TEXT("V2")}};
	TMap<Dummy, DummyBig> custom_struct_key_map{
		{
			Dummy{1, 2, 3}, DummyBig{5, {0}}
		},
		{
			Dummy{4, 5, 6}, DummyBig{7, {1}}
		}
	};

	TMap<E, int> enum_map{{E0, 0}, {E1, 1}, {E2, 2}};
	TMap<EC, int> enum_class_map{{EC::E0, 0}, {EC::E1, 1}, {EC::E2, 2}};

	nop(); // BREAK HERE
}

void TestTSet()
{
	TSet<int> empty;
	TSet<int> simple_set{1, 2, 3};
	TSet<FName> complex_set{TEXT("K1"), TEXT("V1"), TEXT("K2"), TEXT("V2")};
	TSet<Dummy> custom_struct_set{
		Dummy{1, 2, 3},
		Dummy{4, 5, 6}
	};

	nop(); // BREAK HERE
}

void TestTOptional()
{
	TOptional<int> empty;
	TOptional<int> simple_val{5};
	TOptional<FName> complex_val{TEXT("VAL")};
	TOptional<Dummy> custom_struct_val{Dummy{1, 2, 3}};

	nop(); // BREAK HERE
}

void TestTUnion()
{
	TUnion<Dummy, FName, int> empty;
	TUnion<Dummy, FName, int> int_val{5};
	TUnion<Dummy, FName, int> fname_val{FName(TEXT("VAL"))};
	TUnion<Dummy, FName, int> dummy_val{Dummy{1, 2, 3}};

	nop(); // BREAK HERE
}

void TestTTuple()
{
	TTuple<> empty0{};
	TTuple<int> empty1;
	TTuple<int, FName> empty2;
	TTuple<int, FName, Dummy> empty3;

	TTuple<int> tuple1{5};
	TTuple<int, FName> tuple2{3, TEXT("VAL")};
	TTuple<int, FName, Dummy> tuple3{7, TEXT("VAL2"), Dummy{3, 4, 5}};

	nop(); // BREAK HERE
}

int _global_func()
{
	return 5;
}

void TestFunction()
{
	TFunction<int (void)> empty;
	TFunction<int (void)> global = _global_func;
	TFunction<int (void)> lambda = [fn = _global_func]() { return fn(); };

	nop(); // BREAK HERE
}

void TestTRange()
{
	TRange<int> empty;
	TRange<int> closed{TRangeBound<int>::Inclusive(5), TRangeBound<int>::Inclusive(6)};
	TRange<int> inf{TRangeBound<int>::Open(), TRangeBound<int>::Open()};

	nop(); // BREAK HERE
}

void TestTVariant()
{
	TVariant<int> empty{};
	TVariant<int> simple{TInPlaceType<int>{}, 1};
	TVariant<FName> complex{TInPlaceType<FName>{}, TEXT("TEXT")};
	TVariant<Dummy> custom_struct{TInPlaceType<Dummy>{}, Dummy{1, 2, 3,}};
	TVariant<int, FName> v2{TInPlaceType<FName>{}, TEXT("TEXT")};
	TVariant<int, FName, Dummy> v3{TInPlaceType<FName>{}, TEXT("TEXT")};

	nop(); // BREAK HERE
}

// ReSharper enable CppDeclaratorNeverUsed

#define LOCTEXT_NAMESPACE "FNatvisTestModule"


void FNatvisTestModule::StartupModule()
{
	TestFString();
	TestFStringView();
	TestFStringBuilder();
	TestFGuid();
	TestFText();
	TestFName();
	TestFStat();
	TestThreadSafePrimitives();
	TestFTimespan();
	TestFVector();
	TestTEnumAsByte();
	TestTArray();
	TestTPair();
	TestSmartPointers();
	TestTMap();
	TestTSet();
	TestTOptional();
	TestTUnion();
	TestTTuple();
	TestFunction();
	TestTRange();
	TestTVariant();

	// TODO: Test in context of some actor
	//TestUObject();
	//TestFField();
	//TestFGameplayTagContainer();
	//TestFHICommandList();
}

void TestFGameplayTagContainer()
{
	// TODO: can't add tags from code
	// FGameplayTagContainer empty;
	// FGameplayTagContainer single;
	// single.AddTag(FGameplayTag(TEXT("TAG1")));
	// FGameplayTagContainer container;
	// container.AddTag(FGameplayTag(TEXT("TAG1")));
	// container.AddTag(FGameplayTag(TEXT("TAG2")));
	// container.AddTag(FGameplayTag(TEXT("TAG3")));
	// container.AddTag(FGameplayTag(TEXT("TAG4")));

	nop(); // BREAK HERE
}


void FNatvisTestModule::ShutdownModule()
{
}

#undef LOCTEXT_NAMESPACE

IMPLEMENT_MODULE(FNatvisTestModule, NatvisTest)
