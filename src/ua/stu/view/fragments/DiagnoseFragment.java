package ua.stu.view.fragments;

import ua.stu.view.scpview.R;
import android.annotation.TargetApi;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

@TargetApi(11)
public class DiagnoseFragment extends Fragment {
	/**
	 * Диагноз или направления
	 */
	private TextView diagnose;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
		      Bundle savedInstanceState) 
	{
		View v = inflater.inflate(R.layout.diagnose, null);
	    init(v);
		return v;
	}
	
	private final void init(View v)
	{
		this.setDiagnose((TextView)v.findViewById(R.id.diagnose_patient_value));
	}

	public TextView getDiagnose() {
		return diagnose;
	}

	private final void setDiagnose(TextView diagnose) {
		this.diagnose = diagnose;
	}
}